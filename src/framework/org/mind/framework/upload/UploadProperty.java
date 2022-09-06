package org.mind.framework.upload;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * 上传文件成功后的属性值。
 * 包括前段filed的name值，上传后的文件名称、文件路径、文件大小。
 *
 * @author dp
 */
public class UploadProperty {

    private String filedName;

    private String fileName;

    private String filePath;

    private long fileSize;

    private String fileType;

    public UploadProperty(String filedName, String fileName, String filePath, long fileSize, String fileType) {
        this.filedName = filedName;
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileType = fileType;
    }

    public UploadProperty() {

    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFiledName() {
        return filedName;
    }

    public void setFiledName(String filedName) {
        this.filedName = filedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("filedName", filedName)
                .append(" fileName", fileName)
                .append(" path", filePath)
                .append(" size", fileSize)
                .append(" type", fileType)
                .toString();
    }
}
