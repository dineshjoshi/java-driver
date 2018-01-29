/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss.driver.internal.core.data;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.protocol.internal.util.Bytes;
import com.google.common.base.Preconditions;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

public class DefaultUdtValue implements UdtValue {

  private static final long serialVersionUID = 1;

  private final UserDefinedType type;
  private final ByteBuffer[] values;

  public DefaultUdtValue(UserDefinedType type) {
    this(type, new ByteBuffer[type.getFieldTypes().size()]);
  }

  private DefaultUdtValue(UserDefinedType type, ByteBuffer[] values) {
    Preconditions.checkNotNull(type);
    this.type = type;
    this.values = values;
  }

  @Override
  public UserDefinedType getType() {
    return type;
  }

  @Override
  public int size() {
    return values.length;
  }

  @Override
  public int firstIndexOf(CqlIdentifier id) {
    return type.firstIndexOf(id);
  }

  @Override
  public int firstIndexOf(String name) {
    return type.firstIndexOf(name);
  }

  @Override
  public DataType getType(int i) {
    return type.getFieldTypes().get(i);
  }

  @Override
  public ByteBuffer getBytesUnsafe(int i) {
    return values[i];
  }

  @Override
  public UdtValue setBytesUnsafe(int i, ByteBuffer v) {
    values[i] = v;
    return this;
  }

  @Override
  public CodecRegistry codecRegistry() {
    return type.getAttachmentPoint().codecRegistry();
  }

  @Override
  public ProtocolVersion protocolVersion() {
    return type.getAttachmentPoint().protocolVersion();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !(o instanceof UdtValue)){
      return false;
    }

    DefaultUdtValue that = (DefaultUdtValue) o;
    if (!type.equals(that.type))
      return false;
    for(int i = 0; i < values.length; i++) {
      DataType innerThisType=type.getFieldTypes().get(i);
      DataType innerThatType=type.getFieldTypes().get(i);
      if (!innerThisType.equals(innerThatType))
        return false;
      Object thisValue = this.codecRegistry().codecFor(innerThatType).decode(this.values[i], this.protocolVersion());
      Object thatValue = that.codecRegistry().codecFor(innerThatType).decode(that.values[i], that.protocolVersion());

      if (!((thisValue == thatValue) || (thisValue != null && thisValue.equals(thatValue)))) {
        return false;
      }
    }
    return true;

  }


  @Override
  public int hashCode() {

    int result = Objects.hash(type);
    result = 31 * result + Arrays.hashCode(values);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    TypeCodec<Object> codec = this.codecRegistry().codecFor(type);
    sb.append(codec.format(this));
    return sb.toString();
  }

  /**
   * @serialData The type of the tuple, followed by an array of byte arrays representing the values
   *     (null values are represented by {@code null}).
   */
  private Object writeReplace() {
    return new SerializationProxy(this);
  }

  private void readObject(ObjectInputStream stream) throws InvalidObjectException {
    // Should never be called since we serialized a proxy
    throw new InvalidObjectException("Proxy required");
  }

  private static class SerializationProxy implements Serializable {

    private static final long serialVersionUID = 1;

    private final UserDefinedType type;
    private final byte[][] values;

    SerializationProxy(DefaultUdtValue udt) {
      this.type = udt.type;
      this.values = new byte[udt.values.length][];
      for (int i = 0; i < udt.values.length; i++) {
        ByteBuffer buffer = udt.values[i];
        this.values[i] = (buffer == null) ? null : Bytes.getArray(buffer);
      }
    }

    private Object readResolve() {
      ByteBuffer[] buffers = new ByteBuffer[this.values.length];
      for (int i = 0; i < this.values.length; i++) {
        byte[] value = this.values[i];
        buffers[i] = (value == null) ? null : ByteBuffer.wrap(value);
      }
      return new DefaultUdtValue(this.type, buffers);
    }
  }
}
