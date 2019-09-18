package reactor.multipart;

import java.util.Objects;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * @author Arjen Poutsma
 */
public class MultipartParser {

	public static Flux<Part> parse(Publisher<ByteBuf> input, byte[] boundary) {
		Objects.requireNonNull(input);

		return Flux.create(partSink -> {
			PartGenerator processor = new PartGenerator(partSink, boundary);
			partSink.onRequest(processor::onRequest);
			partSink.onCancel(processor::onCancel);

			input.subscribe(processor);
		});
	}



}
