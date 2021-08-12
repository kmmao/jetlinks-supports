package org.jetlinks.supports.test;

import org.jetlinks.core.ProtocolSupport;
import org.jetlinks.core.ProtocolSupports;
import org.jetlinks.core.device.AuthenticationRequest;
import org.jetlinks.core.device.AuthenticationResponse;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.message.codec.DeviceMessageCodec;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.core.metadata.DeviceMetadataCodec;
import org.jetlinks.supports.official.JetLinksDeviceMetadataCodec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

public class MockProtocolSupport implements ProtocolSupport, ProtocolSupports {
    @Nonnull
    @Override
    public String getId() {
        return "test";
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "test";
    }

    @Override
    public Flux<Transport> getSupportedTransport() {
        return Flux.empty();
    }

    @Nonnull
    @Override
    public Mono<DeviceMessageCodec> getMessageCodec(Transport transport) {
        return Mono.empty();
    }


    @Nonnull
    @Override
    public DeviceMetadataCodec getMetadataCodec() {
        return JetLinksDeviceMetadataCodec.getInstance();
    }

    @Nonnull
    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceOperator deviceOperation) {
        return Mono.just(AuthenticationResponse.success(deviceOperation.getDeviceId()));
    }

    @Nonnull
    @Override
    public Mono<AuthenticationResponse> authenticate(@Nonnull AuthenticationRequest request, @Nonnull DeviceRegistry registry) {
        return Mono.just(AuthenticationResponse.success("test"));
    }

    @Override
    public boolean isSupport(String protocol) {
        return true;
    }

    @Override
    public Mono<ProtocolSupport> getProtocol(String protocol) {
        return Mono.just(this);
    }

    @Override
    public Flux<ProtocolSupport> getProtocols() {
        return Flux.just(this);
    }
}
