package org.mind.framework.port;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.mind.framework.dispatcher.handler.MultipartHttpServletRequest;
import org.mind.framework.dispatcher.handler.MultipartHttpServletRequest.FileItem;
import org.mind.framework.exception.NotSupportedException;
import org.mind.framework.util.DateFormatUtils;
import org.mind.framework.util.MatcherUtils;
import org.mind.framework.util.PropertiesUtils;

/**
 * 上传文件
 *
 * @author dongping
 */
public class UploadFile {

    private static Logger logger = Logger.getLogger(UploadFile.class);

    private String directory;

    private String regexType;

    private Properties property;

    private MultipartHttpServletRequest request;


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
     * 如果未设置该项，将使用frame.prperties文件的: upload.dir属性值。
     *
     * @param dir
     * @throws IOException
     * @author dongping
     */
    public UploadFile(HttpServletRequest request, String directory) {
        this.request = (MultipartHttpServletRequest) request;
        this.property = PropertiesUtils.getProperties();
        this.setDirectory(directory);
    }

    private void setDirectory(String directory) {
        File file = new File(directory);

        if (!file.exists())
            file.mkdirs();

        // check directory
        if (!file.isDirectory())
            throw new IllegalArgumentException("Not a directory: " + directory);

        // check writable
        if (!file.canWrite())
            throw new IllegalArgumentException("Not writable: " + directory);

        this.directory = directory;

        regexType = PropertiesUtils.getString(property, "upload.type");
    }


    /**
     * 上传文件到指定目录，如果上传文件全部失败，将返回null.
     *
     * @return UploadProperty类的集合，其中可以获取上传后
     * 文件的存放路径、名称、大小、前台页面file控件name名称
     * @throws IOException
     * @author dongping
     */
    public List<UploadProperty> upload() throws IOException {
        if (this.request.isRequestFailed())
            throw new NotSupportedException("上传的文件大小: " + this.request.getRequestContentLength() + ", 限制上传大小: " + this.request.getDefaultSize());

        long currentTime = DateFormatUtils.getTimeMillis();

		/*
		 * 默认初始化6个上传文件大小 
		 */
        List<UploadProperty> props = new ArrayList<UploadProperty>(6);

        int i = 1;
        StringBuffer sb = new StringBuffer(this.directory.length() + 20);

        // Parse the incoming multipart.
        List<FileItem> list = this.request.getMultipartFiles();

        for (FileItem item : list) {
            String fileName = item.getFileName();
            if (fileName == null)
                continue;
			
			/*
			 * 匹配允许的文件格式
			 */
            String suffix = fileName.substring(fileName.lastIndexOf(".") + 1);
            if (!MatcherUtils.matcher(suffix, regexType, MatcherUtils.IGNORECASE_EQ).matches())
                throw new NotSupportedException(suffix + "类型文件格式不匹配，允许格式有：" + regexType);

            fileName = sb
                    .append(this.directory).append(File.separator).append(currentTime)
                    .append("-").append(i++).append(".")
                    .append(suffix).toString();

            sb.delete(0, sb.length());

            File file = new File(fileName);
            OutputStream out =
                    new BufferedOutputStream(new FileOutputStream(file));

            try {
                out.write(item.getStream());
            } finally {
                out.flush();
                out.close();
            }

//			if(size == 0L){
//				file.delete();
//				logger.warn("上传文件失败，已删除大小为0字节的文件："+ fileName);
//				continue;
//			}

            if (logger.isDebugEnabled())
                logger.debug("upload files: " + fileName);

            props.add(new UploadProperty(
                    item.getFiledName(),
                    file.getName(),
                    fileName,
                    item.getSize(),
                    item.getContentType()));
        }

        if (props.isEmpty())
            props = null;

        return props;
    }

}
