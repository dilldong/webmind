package org.mind.framework.helper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.mind.framework.util.ClassUtils;
import org.mind.framework.util.JsonUtils;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.io.IOException;
import java.util.Objects;

/**
 * @version 1.0
 * @auther Marcus
 * @date 2022/11/26
 */
@Slf4j
public class GsonCodec extends BaseCodec {
    private final Encoder encoder;
    private final Decoder<Object> decoder;

    public GsonCodec() {
        this(false);
    }

    public GsonCodec(boolean expose) {
        this.encoder = in -> {
            ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
            try {
                ByteBufOutputStream os = new ByteBufOutputStream(buf);
                os.writeUTF(JsonUtils.toJson(in, expose));
                os.writeUTF(in.getClass().getName());
                return os.buffer();
            } catch (IOException e) {
                buf.release();
                throw e;
            } catch (Exception e) {
                buf.release();
                throw new IOException(e);
            }
        };

        this.decoder = (buf, state) -> {
            try (ByteBufInputStream stream = new ByteBufInputStream(buf)) {
                String value = stream.readUTF();
                String type = stream.readUTF();
                return JsonUtils.fromJson(value, ClassUtils.getClass(type));
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            }
        };
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (Objects.nonNull(loader))
            return loader;

        return super.getClassLoader();
    }
}
