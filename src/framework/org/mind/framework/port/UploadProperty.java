package org.mind.framework.port;

/**
 * 上传文件成功后的属性值。
 * 包括前台也面file的name值，上传后的文件名称、文件路径、文件大小。
 * 
 * @author dongping
 */
public class UploadProperty {
	
	private String pageName;
	
	private String fileName;
	
	private String filePath;
	
	private long fileSize;
	
	private String fileType;

	public UploadProperty(String pageName, String fileName, String filePath, long fileSize, String fileType) {
		this.pageName = pageName;
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


	public String getPageName() {
		return pageName;
	}

	public void setPageName(String pageName) {
		this.pageName = pageName;
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
		return new StringBuilder()
			.append("[pageName]").append(this.pageName)
			.append(";[fileName]").append(this.fileName)
			.append(";[filePath]").append(this.filePath)
			.append(";[fileSize]").append(this.fileSize)
			.append(";[fileType]").append(this.fileType)
			.toString();
	}
	
	
}
