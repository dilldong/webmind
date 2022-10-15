package org.mind.framework.dispatcher.handler;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.mind.framework.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

/**
 * Wrapper class for multipart HTTP request which usually used to upload file.
 *
 * @author dp
 */
public class MultipartHttpServletRequest extends HttpServletRequestWrapper {

    private static final Logger log = LoggerFactory.getLogger(MultipartHttpServletRequest.class);

    private static final int SIZE = 10 * 1024 * 1024;

    private static final int MAX_SIZE = 16 * 1024 * 1024;

    private static final int MIN_SIZE = 0;

    private static final String DEFAULT_CHARENCODEING = "UTF-8";

    private int defaultSize;

    // multipart fields
    private Map<String, List<String>> formParams;

    // multipart files
    private List<FileItem> multiPartFiles;

    private boolean requestFailed;

    private final int requestContentLength;

    private final HttpServletRequest request;

    private static final String ATTRIBUTE_NAME = MultipartHttpServletRequest.class.getName();

    public static MultipartHttpServletRequest getInstance(HttpServletRequest request) throws IOException, ServletException {
        Object obj = request.getAttribute(ATTRIBUTE_NAME);
        if (obj instanceof MultipartHttpServletRequest)
            return (MultipartHttpServletRequest) obj;

        MultipartHttpServletRequest multipart =
                new MultipartHttpServletRequest(request).requestMultipart();
        request.setAttribute(ATTRIBUTE_NAME, multipart);
        return multipart;
    }


    private MultipartHttpServletRequest(HttpServletRequest request) {
        super(request);
        this.request = request;
        Properties property = PropertiesUtils.getProperties();

        defaultSize = PropertiesUtils.getInteger(property, "upload.size");
        defaultSize = (defaultSize <= MIN_SIZE || defaultSize > MAX_SIZE) ? SIZE : defaultSize;

        // Check the content length to prevent denial of service attacks
        this.requestContentLength = request.getContentLength();
    }

    /**
     * Check the content type to make sure it's "multipart/form-data" Access
     * header two ways to work around WebSphere oddities
     *
     * @param request
     * @return
     */
    public static boolean isMultipartRequest(HttpServletRequest request) {
        String type = null;
        String type1 = request.getHeader("Content-Type");
        String type2 = request.getContentType();

        // If one value is null, choose the other value
        if (type1 == null && type2 != null)
            type = type2;

        else if (type2 == null && type1 != null)
            type = type1;

            // If neither value is null, choose the longer value
        else if (type1 != null)
            type = (type1.length() > type2.length() ? type1 : type2);

        if (type == null)
            return false;

        return type.toLowerCase().startsWith("multipart/form-data");

    }


    private MultipartHttpServletRequest requestMultipart() throws IOException, ServletException {
        if (this.requestContentLength > defaultSize) {
            log.warn("Posted content length of {} exceeds limit of {}", this.requestContentLength, defaultSize);
            this.requestFailed = true;
            return this;
        }

        this.formParams = new HashMap<>();
        this.multiPartFiles = new ArrayList<>(6);

        if (request.getQueryString() != null) {
            this.queryString();
        }

        try {
            FileItemStream filePart;
            FileItemIterator iterator = new ServletFileUpload().getItemIterator(request);
            while (iterator.hasNext()) {
                filePart = iterator.next();

                if (filePart.isFormField()) {
                    this.addFiled(filePart.getFieldName(),
                            Streams.asString(filePart.openStream(), DEFAULT_CHARENCODEING));
                    continue;
                }

                FileItem item = process(filePart.getContentType(), filePart.getFieldName(), filePart.getName(),
                        filePart.openStream());

                if (item != null)
                    multiPartFiles.add(item);
            }
        } catch (FileUploadException e) {
            throw new ServletException(e.getMessage(), e);
        }

        return this;
    }

    private FileItem process(String contentType, String name, String fileName, InputStream in) {
        FileItem item = new FileItem();
        item.setContentType(contentType);
        item.setFiledName(name);
        item.setFileName(fileName);

        long size = 0;
        int read = -1;
        byte[] buf = new byte[8 * 1024];

        ByteArrayOutputStream byteStream;
        try {
            byteStream = new ByteArrayOutputStream();
            try {
                while ((read = in.read(buf)) != -1) {
                    byteStream.write(buf, 0, read);
                    size += read;
                }

            } finally {
                /*
                 * When a close method is called on a such a class, it
                 * automatically performs a flush. There is no need to
                 * explicitly call flush before calling close.
                 *
                 * byteStream.flush();
                 */
                byteStream.close();
            }

            item.setStream(byteStream.toByteArray());
            item.setSize(size);
            return item;

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    private void addFiled(String name, String value) {
        List<String> list = this.formParams.computeIfAbsent(name, k -> new ArrayList<>(5));
        list.add(value);
    }

    private void queryString() {

    }

    /**
     * get multipart files objects by http request.
     *
     * @return
     */
    public List<FileItem> getMultipartFiles() {
        return this.multiPartFiles;
    }

    public String getParameter(String name) {
        if (this.formParams == null)
            return null;

        List<String> list = this.formParams.get(name);
        if (list == null || list.isEmpty())
            return null;

        return list.get(0);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (this.formParams == null)
            return null;

        Map<String, String[]> map = new HashMap<>(this.formParams.size());

        Set<Entry<String, List<String>>> entries = this.formParams.entrySet();
        for (Entry<String, List<String>> en : entries) {
            List<String> list = en.getValue();
            map.put(en.getKey(), list.toArray(new String[0]));
        }
        return map;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (this.formParams == null)
            return null;

        return Collections.enumeration(this.formParams.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        if (this.formParams == null)
            return null;

        List<String> list = this.formParams.get(name);
        return list.toArray(new String[0]);
    }

    public boolean isRequestFailed() {
        return this.requestFailed;
    }

    public int getDefaultSize() {
        return this.defaultSize;
    }

    public int getRequestContentLength() {
        return this.requestContentLength;
    }

    public static class FileItem {
        private String filedName;
        private String fileName;
        private String contentType;
        private byte[] stream;
        private long size;

        public String getFiledName() {
            return filedName;
        }

        public void setFiledName(String filedName) {
            this.filedName = filedName;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public byte[] getStream() {
            return stream;
        }

        public void setStream(byte[] stream) {
            this.stream = stream;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }
    }

}
