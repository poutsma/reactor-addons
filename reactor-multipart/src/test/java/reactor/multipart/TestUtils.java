package reactor.multipart;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Arjen Poutsma
 */
public class TestUtils {


	public static Flux<ByteBuf> fromPath(Path path,
			int maxChunkSize,
			ByteBufAllocator allocator) {
		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(allocator, "allocator");
		if (maxChunkSize < 1) {
			throw new IllegalArgumentException("chunk size must be strictly positive, " + "was: " + maxChunkSize);
		}
		return Flux.generate(
				() -> FileChannel.open(path),
				(fc, sink) -> {
					ByteBuf buf = allocator.buffer(maxChunkSize);
					try {
						if (buf.writeBytes(fc, maxChunkSize) < 0) {
							buf.release();
							sink.complete();
						}
						else {
							sink.next(buf);
						}
					}
					catch (IOException e) {
						buf.release();
						sink.error(e);
					}
					return fc;
				},
				channel -> {
					if (channel.isOpen()) {
						try {
							channel.close();
						}
						catch (IOException ignore) {
						}
					}
				});
	}

	public static Mono<ByteBuf> join(Flux<ByteBuf> flux) {
		return Mono.defer(() -> {
			CompositeByteBuf b = Unpooled.compositeBuffer();
			return flux.reduce(b,
					(prev, next) -> {
						if (prev.refCnt() > 0) {
							return prev.addComponent(true, next.retain());
						}
						else {
							return prev;
						}
					})
					.filter(ByteBuf::isReadable)
					.doFinally(signalType -> {
						if (b.refCnt() > 0) {
							b.release();
						}
					});
		});

	}



}
