/*
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
 */

package org.apache.skywalking.oap.server.receiver.zipkin.trace;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.List;

import java.util.Map;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.TagType;
import org.apache.skywalking.oap.server.core.source.TagAutocomplete;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinService;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinServiceRelation;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinServiceSpan;
import org.apache.skywalking.oap.server.core.zipkin.source.ZipkinSpan;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverConfig;
import zipkin2.Annotation;
import zipkin2.Span;
import zipkin2.internal.RecyclableBuffers;

public class SpanForward {
    private final NamingControl namingControl;
    private final SourceReceiver receiver;
    private final List<String> searchTagKeys;

    public SpanForward(final NamingControl namingControl,
                       final SourceReceiver receiver,
                       final ZipkinReceiverConfig config) {
        this.namingControl = namingControl;
        this.receiver = receiver;
        this.searchTagKeys =  Arrays.asList(config.getSearchableTracesTags().split(Const.COMMA));
    }

    public void send(List<Span> spanList) {
        spanList.forEach(span -> {
            ZipkinSpan zipkinSpan = new ZipkinSpan();
            String serviceName = span.localServiceName();
            if (StringUtil.isEmpty(serviceName)) {
                serviceName = "Unknown";
            }
            zipkinSpan.setSpanId(span.id());
            zipkinSpan.setTraceId(span.traceId());
            zipkinSpan.setSpanId(span.id());
            zipkinSpan.setParentId(span.parentId());
            zipkinSpan.setName(namingControl.formatEndpointName(serviceName, span.name()));
            zipkinSpan.setDuration(span.duration());
            zipkinSpan.setKind(span.kind().name());
            zipkinSpan.setLocalEndpointServiceName(namingControl.formatServiceName(serviceName));
            zipkinSpan.setLocalEndpointIPV4(span.localEndpoint().ipv4());
            zipkinSpan.setLocalEndpointIPV6(span.localEndpoint().ipv6());
            Integer localPort = span.localEndpoint().port();
            if (localPort != null) {
                zipkinSpan.setLocalEndpointPort(localPort);
            }
            zipkinSpan.setRemoteEndpointServiceName(namingControl.formatServiceName(span.remoteServiceName()));
            zipkinSpan.setRemoteEndpointIPV4(span.remoteEndpoint().ipv4());
            zipkinSpan.setRemoteEndpointIPV6(span.remoteEndpoint().ipv6());
            Integer remotePort = span.remoteEndpoint().port();
            if (remotePort != null) {
                zipkinSpan.setRemoteEndpointPort(remotePort);
            }
            zipkinSpan.setTimestamp(span.timestampAsLong());
            zipkinSpan.setDebug(span.debug());
            zipkinSpan.setShared(span.shared());

            long timestampMillis = span.timestampAsLong() / 1000;
            zipkinSpan.setTimestampMillis(timestampMillis);
            long timeBucket = TimeBucket.getRecordTimeBucket(timestampMillis);
            zipkinSpan.setTimeBucket(timeBucket);

            long minuteTimeBucket = TimeBucket.getMinuteTimeBucket(timestampMillis);

            if (!span.tags().isEmpty() || !span.annotations().isEmpty()) {
                List<String> query = zipkinSpan.getQuery();
                JsonObject annotationsJson = new JsonObject();
                JsonObject tagsJson = new JsonObject();
                for (Annotation annotation : span.annotations()) {
                    annotationsJson.addProperty(Long.toString(annotation.timestamp()), annotation.value());
                    if (annotation.value().length() > RecyclableBuffers.SHORT_STRING_LENGTH) {
                        continue;
                    }
                    query.add(annotation.value());
                }
                zipkinSpan.setAnnotations(annotationsJson);
                for (Map.Entry<String, String> tag : span.tags().entrySet()) {
                    String tagString = tag.getKey() + "=" + tag.getValue();
                    tagsJson.addProperty(tag.getKey(), tag.getValue());
                    if (tagString.length() > RecyclableBuffers.SHORT_STRING_LENGTH) {
                        continue;
                    }
                    query.add(tag.getKey());
                    query.add(tagString);

                    if (searchTagKeys.contains(tag.getKey())) {
                        addAutocompleteTags(minuteTimeBucket, tag.getKey(), tag.getValue());
                    }
                }
                zipkinSpan.setTags(tagsJson);
            }
            receiver.receive(zipkinSpan);

            toService(zipkinSpan, minuteTimeBucket);
            toServiceSpan(zipkinSpan, minuteTimeBucket);
            if (!StringUtil.isEmpty(zipkinSpan.getRemoteEndpointServiceName())) {
                toServiceRelation(zipkinSpan, minuteTimeBucket);
            }
        });
    }

    private void addAutocompleteTags(final long minuteTimeBucket, final String key, final String value) {
        TagAutocomplete tagAutocomplete = new TagAutocomplete();
        tagAutocomplete.setTagKey(key);
        tagAutocomplete.setTagValue(value);
        tagAutocomplete.setTagType(TagType.ZIPKIN);
        tagAutocomplete.setTimeBucket(minuteTimeBucket);
        receiver.receive(tagAutocomplete);
    }

    private void toService(ZipkinSpan zipkinSpan, final long minuteTimeBucket) {
        ZipkinService service = new ZipkinService();
        service.setServiceName(zipkinSpan.getLocalEndpointServiceName());
        service.setTimeBucket(minuteTimeBucket);
        receiver.receive(service);
    }

    private void toServiceSpan(ZipkinSpan zipkinSpan, final long minuteTimeBucket) {
        ZipkinServiceSpan serviceSpan = new ZipkinServiceSpan();
        serviceSpan.setServiceName(zipkinSpan.getLocalEndpointServiceName());
        serviceSpan.setSpanName(zipkinSpan.getName());
        serviceSpan.setTimeBucket(minuteTimeBucket);
        receiver.receive(serviceSpan);
    }

    private void toServiceRelation(ZipkinSpan zipkinSpan, final long minuteTimeBucket) {
        ZipkinServiceRelation relation = new ZipkinServiceRelation();
        relation.setServiceName(zipkinSpan.getLocalEndpointServiceName());
        relation.setRemoteServiceName(zipkinSpan.getRemoteEndpointServiceName());
        relation.setTimeBucket(minuteTimeBucket);
        receiver.receive(relation);
    }
}
