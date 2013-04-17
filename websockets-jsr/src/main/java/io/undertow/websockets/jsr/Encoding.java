/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.websockets.jsr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;

import io.undertow.servlet.api.InstanceHandle;

/**
 * Manages all encoders and decoders for an endpoint instance
 *
 * @author Stuart Douglas
 */
public class Encoding implements Closeable {


    private final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> binaryEncoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> binaryDecoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> textEncoders;
    private final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> textDecoders;

    public Encoding(final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> binaryEncoders, final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> binaryDecoders, final Map<Class<?>, List<InstanceHandle<? extends Encoder>>> textEncoders, final Map<Class<?>, List<InstanceHandle<? extends Decoder>>> textDecoders) {
        this.binaryEncoders = binaryEncoders;
        this.binaryDecoders = binaryDecoders;
        this.textEncoders = textEncoders;
        this.textDecoders = textDecoders;
    }


    public boolean canEncodeText(final Class<?> type) {
        if (EncodingFactory.isPrimitiveOrBoxed(type)) {
            return true;
        }
        return textEncoders.containsKey(type);
    }


    public boolean canDecodeText(final Class<?> type) {
        if (EncodingFactory.isPrimitiveOrBoxed(type)) {
            return true;
        }
        return textDecoders.containsKey(type);
    }


    public boolean canEncodeBinary(final Class<?> type) {
        return binaryEncoders.containsKey(type);
    }


    public boolean canDecodeDinary(final Class<?> type) {
        return textDecoders.containsKey(type);
    }


    public Object decodeText(final Class<?> targetType, final String message) throws DecodeException {
        if (EncodingFactory.isPrimitiveOrBoxed(targetType)) {
            return decodePrimitive(targetType, message);
        }
        List<InstanceHandle<? extends Decoder>> decoders = textDecoders.get(targetType);
        if (decoders != null) {
            for (InstanceHandle<? extends Decoder> decoderHandle : decoders) {
                Decoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Decoder.Text) {
                    if (((Decoder.Text) decoder).willDecode(message)) {
                        return ((Decoder.Text) decoder).decode(message);
                    }
                } else {
                    try {
                        return ((Decoder.TextStream) decoder).decode(new StringReader(message));
                    } catch (IOException e) {
                        throw new DecodeException(message, "Could not decode string", e);
                    }
                }
            }
        }
        throw new DecodeException(message, "Could not decode string");
    }

    private Object decodePrimitive(final Class<?> targetType, final String message) throws DecodeException {
        if (targetType == Boolean.class || targetType == boolean.class) {
            return new Boolean(message);
        } else if (targetType == Character.class || targetType == char.class) {
            if (message.length() > 1) {
                throw new DecodeException(message, "Character message larger than 1 character");
            }
            return new Character(message.charAt(0));
        } else if (targetType == Byte.class || targetType == byte.class) {
            return new Byte(message);
        } else if (targetType == Short.class || targetType == short.class) {
            return new Short(message);
        } else if (targetType == Integer.class || targetType == int.class) {
            return new Integer(message);
        } else if (targetType == Long.class || targetType == long.class) {
            return new Long(message);
        } else if (targetType == Float.class || targetType == float.class) {
            return new Float(message);
        } else if (targetType == Double.class || targetType == double.class) {
            return new Double(message);
        }
        return null; // impossible
    }

    public Object decodeBinary(final Class<?> targetType, final byte[] bytes) throws DecodeException {
        List<InstanceHandle<? extends Decoder>> decoders = binaryDecoders.get(targetType);
        if (decoders != null) {
            for (InstanceHandle<? extends Decoder> decoderHandle : decoders) {
                Decoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Decoder.Binary) {
                    if (((Decoder.Binary) decoder).willDecode(ByteBuffer.wrap(bytes))) {
                        return ((Decoder.Binary) decoder).decode(ByteBuffer.wrap(bytes));
                    }
                } else {
                    try {
                        return ((Decoder.BinaryStream) decoder).decode(new ByteArrayInputStream(bytes));
                    } catch (IOException e) {
                        throw new DecodeException(ByteBuffer.wrap(bytes), "Could not decode binary", e);
                    }
                }
            }
        }
        throw new DecodeException(ByteBuffer.wrap(bytes), "Could not decode binary");
    }

    public String encodeText(final Object o) throws EncodeException {
        if (EncodingFactory.isPrimitiveOrBoxed(o.getClass())) {
            return o.toString();
        }
        List<InstanceHandle<? extends Encoder>> decoders = textEncoders.get(o.getClass());
        if (decoders != null) {
            for (InstanceHandle<? extends Encoder> decoderHandle : decoders) {
                Encoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Encoder.Text) {
                    return ((Encoder.Text) decoder).encode(o);
                } else {
                    try {
                        StringWriter out = new StringWriter();
                        ((Encoder.TextStream) decoder).encode(o, out);
                        return out.toString();
                    } catch (IOException e) {
                        throw new EncodeException(o, "Could not encode text", e);
                    }
                }
            }
        }
        throw new EncodeException(o, "Could not encode text");
    }

    public ByteBuffer encodeBinary(final Object o) throws EncodeException {
        List<InstanceHandle<? extends Encoder>> decoders = binaryEncoders.get(o.getClass());
        if (decoders != null) {
            for (InstanceHandle<? extends Encoder> decoderHandle : decoders) {
                Encoder decoder = decoderHandle.getInstance();
                if (decoder instanceof Encoder.Binary) {
                    return ((Encoder.Binary) decoder).encode(o);
                } else {
                    try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ((Encoder.BinaryStream) decoder).encode(o, out);
                        return ByteBuffer.wrap(out.toByteArray());
                    } catch (IOException e) {
                        throw new EncodeException(o, "Could not encode binary", e);
                    }
                }
            }
        }
        throw new EncodeException(o, "Could not encode binary");
    }

    @Override
    public void close() {
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Decoder>>> entry : binaryDecoders.entrySet()) {
            for (InstanceHandle<? extends Decoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Decoder>>> entry : textDecoders.entrySet()) {
            for (InstanceHandle<? extends Decoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : binaryEncoders.entrySet()) {
            for (InstanceHandle<? extends Encoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
        for (Map.Entry<Class<?>, List<InstanceHandle<? extends Encoder>>> entry : textEncoders.entrySet()) {
            for (InstanceHandle<? extends Encoder> val : entry.getValue()) {
                val.getInstance().destroy();
                val.release();
            }
        }
    }
}