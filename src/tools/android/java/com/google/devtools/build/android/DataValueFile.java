// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.google.common.base.MoreObjects;
import com.google.devtools.build.android.proto.SerializeFormat;
import com.google.protobuf.CodedOutputStream;

import com.android.ide.common.res2.MergingException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a file based android resource or asset.
 *
 * These include all resource types except those found in values, as well as all assets.
 */
public class DataValueFile implements DataResource, DataAsset {

  private final Path source;

  private DataValueFile(Path source) {
    this.source = source;
  }

  public static DataValueFile of(Path source) {
    return new DataValueFile(source);
  }

  /**
   * Creates a {@link DataValueFile} from a {@link SerializeFormat.DataValue}.
   */
  public static DataValueFile from(
      SerializeFormat.DataValue protoValue, FileSystem currentFileSystem) {
    return of(currentFileSystem.getPath(protoValue.getSource().getFilename()));
  }

  @Override
  public int hashCode() {
    return source.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof DataValueFile)) {
      return false;
    }
    DataValueFile resource = (DataValueFile) obj;
    return Objects.equals(source, resource.source);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("source", source).toString();
  }

  @Override
  public Path source() {
    return source;
  }

  @Override
  public void writeAsset(RelativeAssetPath key, AndroidDataWritingVisitor mergedDataWriter)
      throws IOException {
    mergedDataWriter.copyAsset(source, key.toPathString());
  }

  @Override
  public void writeResource(FullyQualifiedName key, AndroidDataWritingVisitor mergedDataWriter)
      throws IOException, MergingException {
    mergedDataWriter.copyResource(source, key.toPathString(getSourceExtension()));
  }

  @Override
  public int serializeTo(DataKey key, OutputStream output) throws IOException {
    SerializeFormat.DataValue.Builder builder = SerializeFormat.DataValue.newBuilder();
    SerializeFormat.DataValue value =
        builder.setSource(builder.getSourceBuilder().setFilename(source.toString())).build();
    value.writeDelimitedTo(output);
    return CodedOutputStream.computeUInt32SizeNoTag(value.getSerializedSize())
        + value.getSerializedSize();
  }

  private String getSourceExtension() {
    // TODO(corysmith): Find out if there is a filename parser utility.
    String fileName = source.getFileName().toString();
    int extensionStart = fileName.lastIndexOf('.');
    if (extensionStart > 0) {
      return fileName.substring(extensionStart);
    }
    return "";
  }

  @Override
  public DataResource combineWith(DataResource resource) {
    throw new IllegalArgumentException(getClass() + " does not combine.");
  }
}
