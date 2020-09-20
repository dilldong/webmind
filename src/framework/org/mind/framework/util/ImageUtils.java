package org.mind.framework.util;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import com.sun.image.codec.jpeg.ImageFormatException;
import com.sun.image.codec.jpeg.JPEGCodec;
import com.sun.image.codec.jpeg.JPEGEncodeParam;
import com.sun.image.codec.jpeg.JPEGImageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(ImageUtils.class);
	
	/**
	 * 为图片添加水印处理
	 * @param imageStream 源图片
	 * @param savePath 保存图片路径(JPG格式)
	 * @param markPath 水印图片路径
	 */
	public static void mark(InputStream imageStream, String savePath, String markPath){
		try {
			mark(
					imageStream, 
					new BufferedOutputStream(new FileOutputStream(savePath)), 
					markPath);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 为图片添加水印处理
	 * @param imageStream 源图片
	 * @param out 输出图片(输出JPG格式)
	 * @param markPath 水印图片路径
	 */
	public static void mark(InputStream imageStream, OutputStream out, String markPath){
		try {
			mark(
					ImageIO.read(imageStream), 
					out, 
					ImageIO.read(new BufferedInputStream(new FileInputStream(markPath))), 0, 0, 0.5F, 0.75F);
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 为图片添加水印处理
	 * @param sourceImage 源图片
	 * @param out 输出图片(输出JPG格式)
	 * @param markImage 水印图片
	 * @param alpha 透明度0~1间, 默认0.5
	 * @param quality 图片质量0~1间, 默认0.95
	 */
	public static void mark(BufferedImage sourceImage, OutputStream out, BufferedImage markImage, int x, int y, float alpha, float quality) {
		try{
			byte[] data = mark(sourceImage, markImage, x, y, alpha, quality);
			if(data == null){
				out.flush();
				return;
			}
			
			out.write(data);
			out.close();
//			ImageIO.write(image, "PNG", out);
		}catch(IOException e){
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 为图片添加水印处理
	 * @param sourceImage 源图片
	 * @param markImage 水印图片
	 * @param alpha 透明度0~1间, 默认0.5
	 * @param quality 图片质量0~1间, 默认0.95
	 * @return byte[]
	 */
	public static byte[] mark(BufferedImage sourceImage, BufferedImage markImage, int x, int y, float alpha, float quality) {
		if(alpha < 0 || alpha > 1)
			alpha = 0.5F;
		
		if(sourceImage == null)
			throw new IllegalArgumentException("source image is null.");
		
		if(markImage == null)
			throw new IllegalArgumentException("mark image is null.");
		
		int width = sourceImage.getWidth();
		int height = sourceImage.getHeight();
		
		// new image
		BufferedImage image = 
				new BufferedImage(width, height, BufferedImage.TYPE_INT_BGR);
		
		Graphics2D g = image.createGraphics();
		g.drawImage(sourceImage, 0, 0, width, height, null);
		
		//在已经绘制的图片中加入透明度通道，透明度0~1间
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
		
		g.drawImage(markImage, x, y, markImage.getWidth(),  markImage.getHeight(), null);
		g.dispose();
		image.flush();
		sourceImage.flush();
		markImage.flush();
		
		return toByteArray(image, quality);
	}
	
	public static byte[] toByteArray(BufferedImage image, float quality){
		if(quality < 0 || quality > 1)
			quality = 0.75F;
		
		ByteArrayOutputStream dest = new ByteArrayOutputStream();
		JPEGEncodeParam param = JPEGCodec.getDefaultJPEGEncodeParam(image);
        param.setQuality(quality, false);
		JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(dest, param);
		try{
			encoder.encode(image);
			byte[] data = dest.toByteArray();
			image.flush();
			dest.close();
			return data;
		}catch(ImageFormatException e){
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	
	/**
	 * 保存图片
	 * @param path 保存的路径
	 * @param image 图片源
	 * @param quality 保存的质量，0~1间
	 */
	public static void save(String path, BufferedImage image, float quality){
		try {
			BufferedOutputStream imgOut = 
						new BufferedOutputStream(
								new FileOutputStream(path));
			
			byte[] data = ImageUtils.toByteArray(image, 1F);
   			imgOut.write(data);
   			imgOut.close();
		} catch (FileNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 保存图片
	 * @param path 保存的路径
	 * @param imageIn 图片源
	 * @param quality 保存的质量，0~1间
	 */
	public static void save(String path, InputStream imageIn, float quality){
		try {
			save(path, ImageIO.read(imageIn), quality);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
	

	/**
	 * 裁剪图片
	 * @param imageStream 原图片
	 * @param outStream 保存对象（可以是file或http）
	 * @param subBounds xy以及宽高
	 * @param quality 图片质量0~1间，默认0.75
	 * @return byte[]
	 */
	public static void cut(BufferedImage imageStream, OutputStream outStream, Rectangle subBounds, float quality) {
		byte[] data = cut(imageStream, subBounds, quality);
		try{
			if(data == null){
				outStream.flush();
				return;
			}
				
			outStream.write(data);
			outStream.close();
		}catch(IOException e){
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * 裁剪图片
	 * @param imageStream 原图片
	 * @param subBounds xy坐标以及宽高
	 * @param quality 图片质量0~1间，默认0.75
	 * @return byte[]
	 */
	public static byte[] cut(BufferedImage imageStream, Rectangle subBounds, float quality) {
		int srcWidth = imageStream.getWidth(); // 源图宽度
		int srcHeight = imageStream.getHeight(); // 源图高度
		
		if (srcWidth >= subBounds.width && srcHeight >= subBounds.height) {
			if(subBounds.width > srcWidth - subBounds.x)
				subBounds.width = srcWidth - subBounds.x;
			
			if(subBounds.height > srcHeight - subBounds.y)
				subBounds.height = srcHeight - subBounds.y;
			
			BufferedImage tagImage = 
					imageStream.getSubimage(subBounds.x, subBounds.y, subBounds.width, subBounds.height);
			
			return toByteArray(tagImage, quality);
		}
		
		return null;
	}
	
}
