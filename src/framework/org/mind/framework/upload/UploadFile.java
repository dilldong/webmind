package org.mind.framework.upload;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mind.framework.dispatcher.handler.MultipartHttpServletRequest;
import org.mind.framework.dispatcher.handler.MultipartHttpServletRequest.FileItem;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Upload files
 *
 * @author Marcus
 */
public class UploadFile {

    private static final Logger log = LoggerFactory.getLogger(UploadFile.class);

    private String directory;

    private String regexType;

    private final Properties property;

    private final MultipartHttpServletRequest request;


    public UploadFile(HttpServletRequest request) {
        this.request = (MultipartHttpServletRequest) request;
        this.property = PropertiesUtils.getProperties();

        String dir = PropertiesUtils.getString(this.property, "upload.dir");
        dir = dir.startsWith("/") ?
                dir :
                request.getSession().getServletContext().getRealPath("/" + dir);

        this.setDirectory(dir);
    }

    /**
     * 需要设置文件的存放路径；
     * 如果未设置该项，将使用frame.properties文件的: upload.dir属性值。
     *
     * @throws IOException
     * @author Marcus
     */
    public UploadFile(HttpServletRequest request, String directory) {
        this.request = (MultipartHttpServletRequest) request;
        this.property = PropertiesUtils.getProperties();
        this.setDirectory(directory);
    }

    private void setDirectory(String directory) {
        File file = new File(directory);

        if (!file.exists()) {
            try {
                FileUtils.forceMkdir(file);
            } catch (IOException e) {
                log.warn("Creating file-directory error: {}", e.getMessage());
            }
        }

        // check directory
        if (!file.isDirectory())
            throw new IllegalArgumentException(String.format("Not a directory: %s", directory));

        // check writable
        if (!file.canWrite())
            throw new IllegalArgumentException(String.format("Not writable: %s", directory));

        this.directory = directory;
        regexType = PropertiesUtils.getString(property, "upload.type");
    }


    /**
     * 上传文件到指定目录，如果上传文件全部失败，将返回null.
     *
     * @return UploadProperty类的集合，其中可以获取上传后
     * 文件的存放路径、名称、大小、前台页面file控件name名称
     * @throws IOException
     * @author Marcus
     */
    public List<UploadProperty> upload() throws IOException {
        if (this.request.isRequestFailed())
            throw new NotSupportedException(
                    String.format("上传的文件大小: %d, 限制大小: %d",
                            this.request.getRequestContentLength(), this.request.getDefaultSize()));

        long currentTime = DateFormatUtils.getMillis();

        /*
         * 默认初始化6个上传文件大小
         */
        List<UploadProperty> props = new ArrayList<>(6);

        int i = 1;
        StringBuilder sb = new StringBuilder(this.directory.length() + 20);

        // Parse the incoming multipart.
        List<FileItem> list = this.request.getMultipartFiles();

        for (FileItem item : list) {
            String fileName = item.getFileName();
            if (StringUtils.isEmpty(fileName))
                continue;

            /*
             * 匹配允许的文件格式
             */
            String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
            if (!MatcherUtils.matcher(suffix, regexType, MatcherUtils.IGNORECASE_EQ).matches())
                throw new NotSupportedException(String.format("%s file suffix mismatch, allowed suffix: %s", suffix, regexType));

            fileName = sb
                    .append(this.directory).append(File.separator).append(currentTime)
                    .append("-").append(i++).append(".")
                    .append(suffix).toString();

            sb.delete(0, sb.length());

            File file = new File(fileName);
            OutputStream out =
                    new BufferedOutputStream(Files.newOutputStream(file.toPath()));

            try {
                out.write(item.getStream());
            } finally {
                out.flush();
                out.close();
            }

            log.debug("upload files: {}", fileName);

            props.add(new UploadProperty(
                    item.getFiledName(),
                    file.getName(),
                    fileName,
                    item.getSize(),
                    item.getContentType()));
        }

        return props;
    }

}
