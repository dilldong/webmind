package org.mind.framework.util;

import org.springframework.core.io.FileSystemResource;

public class PropertyPlaceholderConfigurer extends org.springframework.beans.factory.config.PropertyPlaceholderConfigurer {

	private static String basePath = "config";

	public void setLocationPaths(String[] locations) {
		if (locations != null) {
			FileSystemResource[] fs = new FileSystemResource[locations.length];

			for (int i = 0; i < locations.length; ++i) {
				fs[i] = new FileSystemResource(basePath + locations[i]);
			}
			super.setLocations(fs);
		}

	}

	public static void setBasePath(String p) {
		basePath = p;
	}

}