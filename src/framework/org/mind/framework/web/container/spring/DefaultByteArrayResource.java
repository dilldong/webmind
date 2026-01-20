package org.mind.framework.web.container.spring;

import org.jetbrains.annotations.NotNull;
import org.mind.framework.util.IOUtils;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author: Marcus
 * @date: 2026/1/20
 * @version: 1.0
 */
public class DefaultByteArrayResource extends ByteArrayResource {
    private final String path;

    public DefaultByteArrayResource(InputStream in, String path) throws IOException {
        this(IOUtils.readBytes(in), path);
    }

    public DefaultByteArrayResource(byte[] byteArray, String path) {
        super(byteArray);
        this.path = path;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "JAR resource [" + this.path + "]";
    }
}
