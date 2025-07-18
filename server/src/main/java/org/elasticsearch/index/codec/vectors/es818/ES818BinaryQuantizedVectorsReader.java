/*
 * @notice
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright (C) 2024 Elasticsearch B.V.
 */
package org.elasticsearch.index.codec.vectors.es818;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.hnsw.FlatVectorsReader;
import org.apache.lucene.codecs.lucene95.OrdToDocDISIReaderConfiguration;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.store.ChecksumIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.ReadAdvice;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.SuppressForbidden;
import org.apache.lucene.util.hnsw.OrdinalTranslatedKnnCollector;
import org.apache.lucene.util.hnsw.RandomVectorScorer;
import org.elasticsearch.index.codec.vectors.BQVectorUtils;
import org.elasticsearch.index.codec.vectors.OptimizedScalarQuantizer;
import org.elasticsearch.index.codec.vectors.reflect.OffHeapByteSizeUtils;
import org.elasticsearch.index.codec.vectors.reflect.OffHeapStats;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsReader.readSimilarityFunction;
import static org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsReader.readVectorEncoding;
import static org.elasticsearch.index.codec.vectors.es818.ES818BinaryQuantizedVectorsFormat.VECTOR_DATA_EXTENSION;

/**
 * Copied from Lucene, replace with Lucene's implementation sometime after Lucene 10
 */
@SuppressForbidden(reason = "Lucene classes")
public class ES818BinaryQuantizedVectorsReader extends FlatVectorsReader implements OffHeapStats {

    private static final long SHALLOW_SIZE = RamUsageEstimator.shallowSizeOfInstance(ES818BinaryQuantizedVectorsReader.class);

    private final Map<String, FieldEntry> fields;
    private final IndexInput quantizedVectorData;
    private final FlatVectorsReader rawVectorsReader;
    private final ES818BinaryFlatVectorsScorer vectorScorer;

    @SuppressWarnings("this-escape")
    ES818BinaryQuantizedVectorsReader(
        SegmentReadState state,
        FlatVectorsReader rawVectorsReader,
        ES818BinaryFlatVectorsScorer vectorsScorer
    ) throws IOException {
        super(vectorsScorer);
        this.fields = new HashMap<>();
        this.vectorScorer = vectorsScorer;
        this.rawVectorsReader = rawVectorsReader;
        int versionMeta = -1;
        String metaFileName = IndexFileNames.segmentFileName(
            state.segmentInfo.name,
            state.segmentSuffix,
            org.elasticsearch.index.codec.vectors.es818.ES818BinaryQuantizedVectorsFormat.META_EXTENSION
        );
        boolean success = false;
        try (ChecksumIndexInput meta = state.directory.openChecksumInput(metaFileName)) {
            Throwable priorE = null;
            try {
                versionMeta = CodecUtil.checkIndexHeader(
                    meta,
                    ES818BinaryQuantizedVectorsFormat.META_CODEC_NAME,
                    ES818BinaryQuantizedVectorsFormat.VERSION_START,
                    ES818BinaryQuantizedVectorsFormat.VERSION_CURRENT,
                    state.segmentInfo.getId(),
                    state.segmentSuffix
                );
                readFields(meta, state.fieldInfos);
            } catch (Throwable exception) {
                priorE = exception;
            } finally {
                CodecUtil.checkFooter(meta, priorE);
            }
            quantizedVectorData = openDataInput(
                state,
                versionMeta,
                VECTOR_DATA_EXTENSION,
                ES818BinaryQuantizedVectorsFormat.VECTOR_DATA_CODEC_NAME,
                // Quantized vectors are accessed randomly from their node ID stored in the HNSW
                // graph.
                state.context.withReadAdvice(ReadAdvice.RANDOM)
            );
            success = true;
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(this);
            }
        }
    }

    private ES818BinaryQuantizedVectorsReader(ES818BinaryQuantizedVectorsReader clone, FlatVectorsReader rawVectorsReader) {
        super(clone.vectorScorer);
        this.rawVectorsReader = rawVectorsReader;
        this.vectorScorer = clone.vectorScorer;
        this.quantizedVectorData = clone.quantizedVectorData;
        this.fields = clone.fields;
    }

    // For testing
    FlatVectorsReader getRawVectorsReader() {
        return rawVectorsReader;
    }

    @Override
    public FlatVectorsReader getMergeInstance() {
        return new ES818BinaryQuantizedVectorsReader(this, rawVectorsReader.getMergeInstance());
    }

    private void readFields(ChecksumIndexInput meta, FieldInfos infos) throws IOException {
        for (int fieldNumber = meta.readInt(); fieldNumber != -1; fieldNumber = meta.readInt()) {
            FieldInfo info = infos.fieldInfo(fieldNumber);
            if (info == null) {
                throw new CorruptIndexException("Invalid field number: " + fieldNumber, meta);
            }
            FieldEntry fieldEntry = readField(meta, info);
            validateFieldEntry(info, fieldEntry);
            fields.put(info.name, fieldEntry);
        }
    }

    static void validateFieldEntry(FieldInfo info, FieldEntry fieldEntry) {
        int dimension = info.getVectorDimension();
        if (dimension != fieldEntry.dimension) {
            throw new IllegalStateException(
                "Inconsistent vector dimension for field=\"" + info.name + "\"; " + dimension + " != " + fieldEntry.dimension
            );
        }

        int binaryDims = BQVectorUtils.discretize(dimension, 64) / 8;
        long numQuantizedVectorBytes = Math.multiplyExact((binaryDims + (Float.BYTES * 3) + Short.BYTES), (long) fieldEntry.size);
        if (numQuantizedVectorBytes != fieldEntry.vectorDataLength) {
            throw new IllegalStateException(
                "Binarized vector data length "
                    + fieldEntry.vectorDataLength
                    + " not matching size = "
                    + fieldEntry.size
                    + " * (binaryBytes="
                    + binaryDims
                    + " + 14"
                    + ") = "
                    + numQuantizedVectorBytes
            );
        }
    }

    @Override
    public RandomVectorScorer getRandomVectorScorer(String field, float[] target) throws IOException {
        FieldEntry fi = fields.get(field);
        if (fi == null || fi.size() == 0) {
            return null;
        }
        return vectorScorer.getRandomVectorScorer(
            fi.similarityFunction,
            OffHeapBinarizedVectorValues.load(
                fi.ordToDocDISIReaderConfiguration,
                fi.dimension,
                fi.size,
                new OptimizedScalarQuantizer(fi.similarityFunction),
                fi.similarityFunction,
                vectorScorer,
                fi.centroid,
                fi.centroidDP,
                fi.vectorDataOffset,
                fi.vectorDataLength,
                quantizedVectorData
            ),
            target
        );
    }

    @Override
    public RandomVectorScorer getRandomVectorScorer(String field, byte[] target) throws IOException {
        return rawVectorsReader.getRandomVectorScorer(field, target);
    }

    @Override
    public void checkIntegrity() throws IOException {
        rawVectorsReader.checkIntegrity();
        CodecUtil.checksumEntireFile(quantizedVectorData);
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) throws IOException {
        FieldEntry fi = fields.get(field);
        if (fi == null) {
            return null;
        }
        if (fi.vectorEncoding != VectorEncoding.FLOAT32) {
            throw new IllegalArgumentException(
                "field=\"" + field + "\" is encoded as: " + fi.vectorEncoding + " expected: " + VectorEncoding.FLOAT32
            );
        }
        OffHeapBinarizedVectorValues bvv = OffHeapBinarizedVectorValues.load(
            fi.ordToDocDISIReaderConfiguration,
            fi.dimension,
            fi.size,
            new OptimizedScalarQuantizer(fi.similarityFunction),
            fi.similarityFunction,
            vectorScorer,
            fi.centroid,
            fi.centroidDP,
            fi.vectorDataOffset,
            fi.vectorDataLength,
            quantizedVectorData
        );
        return new BinarizedVectorValues(rawVectorsReader.getFloatVectorValues(field), bvv);
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) throws IOException {
        return rawVectorsReader.getByteVectorValues(field);
    }

    @Override
    public void search(String field, byte[] target, KnnCollector knnCollector, Bits acceptDocs) throws IOException {
        rawVectorsReader.search(field, target, knnCollector, acceptDocs);
    }

    @Override
    public void search(String field, float[] target, KnnCollector knnCollector, Bits acceptDocs) throws IOException {
        if (knnCollector.k() == 0) return;
        final RandomVectorScorer scorer = getRandomVectorScorer(field, target);
        if (scorer == null) return;
        OrdinalTranslatedKnnCollector collector = new OrdinalTranslatedKnnCollector(knnCollector, scorer::ordToDoc);
        Bits acceptedOrds = scorer.getAcceptOrds(acceptDocs);
        for (int i = 0; i < scorer.maxOrd(); i++) {
            if (acceptedOrds == null || acceptedOrds.get(i)) {
                collector.collect(i, scorer.score(i));
                collector.incVisitedCount(1);
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOUtils.close(quantizedVectorData, rawVectorsReader);
    }

    @Override
    public long ramBytesUsed() {
        long size = SHALLOW_SIZE;
        size += RamUsageEstimator.sizeOfMap(fields, RamUsageEstimator.shallowSizeOfInstance(FieldEntry.class));
        size += rawVectorsReader.ramBytesUsed();
        return size;
    }

    @Override
    public Map<String, Long> getOffHeapByteSize(FieldInfo fieldInfo) {
        Objects.requireNonNull(fieldInfo);
        var raw = OffHeapByteSizeUtils.getOffHeapByteSize(rawVectorsReader, fieldInfo);
        var fieldEntry = fields.get(fieldInfo.name);
        if (fieldEntry == null) {
            assert fieldInfo.getVectorEncoding() == VectorEncoding.BYTE;
            return raw;
        }
        var quant = Map.of(VECTOR_DATA_EXTENSION, fieldEntry.vectorDataLength());
        return OffHeapByteSizeUtils.mergeOffHeapByteSizeMaps(raw, quant);
    }

    public float[] getCentroid(String field) {
        FieldEntry fieldEntry = fields.get(field);
        if (fieldEntry != null) {
            return fieldEntry.centroid;
        }
        return null;
    }

    private static IndexInput openDataInput(
        SegmentReadState state,
        int versionMeta,
        String fileExtension,
        String codecName,
        IOContext context
    ) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, fileExtension);
        IndexInput in = state.directory.openInput(fileName, context);
        boolean success = false;
        try {
            int versionVectorData = CodecUtil.checkIndexHeader(
                in,
                codecName,
                ES818BinaryQuantizedVectorsFormat.VERSION_START,
                ES818BinaryQuantizedVectorsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(),
                state.segmentSuffix
            );
            if (versionMeta != versionVectorData) {
                throw new CorruptIndexException(
                    "Format versions mismatch: meta=" + versionMeta + ", " + codecName + "=" + versionVectorData,
                    in
                );
            }
            CodecUtil.retrieveChecksum(in);
            success = true;
            return in;
        } finally {
            if (success == false) {
                IOUtils.closeWhileHandlingException(in);
            }
        }
    }

    private FieldEntry readField(IndexInput input, FieldInfo info) throws IOException {
        VectorEncoding vectorEncoding = readVectorEncoding(input);
        VectorSimilarityFunction similarityFunction = readSimilarityFunction(input);
        if (similarityFunction != info.getVectorSimilarityFunction()) {
            throw new IllegalStateException(
                "Inconsistent vector similarity function for field=\""
                    + info.name
                    + "\"; "
                    + similarityFunction
                    + " != "
                    + info.getVectorSimilarityFunction()
            );
        }
        return FieldEntry.create(input, vectorEncoding, info.getVectorSimilarityFunction());
    }

    private record FieldEntry(
        VectorSimilarityFunction similarityFunction,
        VectorEncoding vectorEncoding,
        int dimension,
        int descritizedDimension,
        long vectorDataOffset,
        long vectorDataLength,
        int size,
        float[] centroid,
        float centroidDP,
        OrdToDocDISIReaderConfiguration ordToDocDISIReaderConfiguration
    ) {

        static FieldEntry create(IndexInput input, VectorEncoding vectorEncoding, VectorSimilarityFunction similarityFunction)
            throws IOException {
            int dimension = input.readVInt();
            long vectorDataOffset = input.readVLong();
            long vectorDataLength = input.readVLong();
            int size = input.readVInt();
            final float[] centroid;
            float centroidDP = 0;
            if (size > 0) {
                centroid = new float[dimension];
                input.readFloats(centroid, 0, dimension);
                centroidDP = Float.intBitsToFloat(input.readInt());
            } else {
                centroid = null;
            }
            OrdToDocDISIReaderConfiguration conf = OrdToDocDISIReaderConfiguration.fromStoredMeta(input, size);
            return new FieldEntry(
                similarityFunction,
                vectorEncoding,
                dimension,
                BQVectorUtils.discretize(dimension, 64),
                vectorDataOffset,
                vectorDataLength,
                size,
                centroid,
                centroidDP,
                conf
            );
        }
    }

    /** Binarized vector values holding row and quantized vector values */
    protected static final class BinarizedVectorValues extends FloatVectorValues {
        private final FloatVectorValues rawVectorValues;
        private final BinarizedByteVectorValues quantizedVectorValues;

        BinarizedVectorValues(FloatVectorValues rawVectorValues, BinarizedByteVectorValues quantizedVectorValues) {
            this.rawVectorValues = rawVectorValues;
            this.quantizedVectorValues = quantizedVectorValues;
        }

        @Override
        public int dimension() {
            return rawVectorValues.dimension();
        }

        @Override
        public int size() {
            return rawVectorValues.size();
        }

        @Override
        public float[] vectorValue(int ord) throws IOException {
            return rawVectorValues.vectorValue(ord);
        }

        @Override
        public BinarizedVectorValues copy() throws IOException {
            return new BinarizedVectorValues(rawVectorValues.copy(), quantizedVectorValues.copy());
        }

        @Override
        public Bits getAcceptOrds(Bits acceptDocs) {
            return rawVectorValues.getAcceptOrds(acceptDocs);
        }

        @Override
        public int ordToDoc(int ord) {
            return rawVectorValues.ordToDoc(ord);
        }

        @Override
        public DocIndexIterator iterator() {
            return rawVectorValues.iterator();
        }

        @Override
        public VectorScorer scorer(float[] query) throws IOException {
            return quantizedVectorValues.scorer(query);
        }

        BinarizedByteVectorValues getQuantizedVectorValues() throws IOException {
            return quantizedVectorValues;
        }
    }
}
