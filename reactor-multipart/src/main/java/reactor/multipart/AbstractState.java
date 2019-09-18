package reactor.multipart;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.Logger;
import reactor.util.Loggers;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * @author Arjen Poutsma
 */
abstract class AbstractState implements State, Disposable {

	static final Logger log = Loggers.getLogger(PartGenerator.class);

	private final PartGenerator generator;

	private final byte[] boundary;

	private static final byte CR = '\r';

	private static final byte LF = '\n';

	private static final byte HYPHEN = '-';

	private static final byte[] TWO_HYPHENS = {HYPHEN, HYPHEN};

	private static final byte[] CR_LF = {CR, LF};

	protected AbstractState(PartGenerator generator, byte[] boundary) {
		this.generator = generator;
		this.boundary = boundary;
	}

	public static AbstractState preamble(PartGenerator generator, byte[] boundary) {
		return new PreambleState(generator, boundary);
	}

	protected AbstractState headers(ByteBufAllocator allocator) {
		return new HeadersState(this.generator, this.boundary, allocator);
	}

	protected AbstractState body(FluxSink<ByteBuf> bodySink) {
		return new BodyState(this.generator, this.boundary, bodySink);
	}

	protected AbstractState completed() {
		return CompletedState.INSTANCE;
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		subscription.request(1);
	}

	@Override
	public void onComplete() {
		this.generator.emitComplete();
	}

	@Override
	public void onError(Throwable throwable) {
		this.generator.emitError(throwable);
	}

	@Override
	public void onRequest(long n) {
	}

	@Override
	public void onCancel() {
		this.generator.cancel();
	}

	@Override
	public void dispose() {
	}

	protected void requestByteBuf() {
		if (log.isTraceEnabled()) {
			log.trace("Requesting buffer");
		}
		this.generator.request(1);
	}

	protected void emitPart(Part part) {
		this.generator.emitPart(part);
	}

	protected void emitError(Throwable t) {
		this.generator.emitError(t);
	}

	protected void emitComplete() {
		this.generator.emitComplete();
	}

	protected void changeState(State newState, ByteBuf remainder) {
		this.generator.changeState(newState);
		if (remainder.isReadable()) {
			newState.onNext(remainder);
		}
		else {
			remainder.release();
			requestByteBuf();
		}
	}

	private static byte[] concat(byte[]... byteArrays) {
		int length = 0;
		for (byte[] byteArray : byteArrays) {
			length += byteArray.length;
		}
		byte[] result = new byte[length];
		length = 0;
		for (byte[] byteArray : byteArrays) {
			System.arraycopy(byteArray, 0, result, length, byteArray.length);
			length += byteArray.length;
		}
		return result;
	}

	private static ByteBuf sliceTo(ByteBuf buf, int idx) {
		int len = idx - buf.readerIndex() + 1;
		return buf.retainedSlice(buf.readerIndex(), len);
	}

	private static ByteBuf sliceFrom(ByteBuf buf, int idx) {
		int len = buf.writerIndex() - idx - 1;
		return buf.retainedSlice(idx + 1, len);
	}

	static String toString(ByteBuf buf) {
		if (buf == null) {
			return "null";
		}
		byte[] bytes = new byte[buf.readableBytes()];
		int j = 0;
		for (int i = buf.readerIndex(); i < buf.writerIndex(); i++) {
			bytes[j++] = buf.getByte(i);
		}
		return toString(bytes);
	}

	private static String toString(byte[] bytes) {
		StringBuilder builder = new StringBuilder("\"");
		for (byte b : bytes) {
			if (b == CR) {
				builder.append("␍");
			}
			else if (b == LF) {
				builder.append("␤");
			}
			else if (b >= 20 && b <= 126) {
				builder.append((char) b);
			}
		}
		builder.append('"');
		return builder.toString();
	}

	private static class PreambleState extends AbstractState {

		private final ByteBufMatcher firstBoundary;

		public PreambleState(PartGenerator generator, byte[] boundary) {
			super(generator, boundary);
			this.firstBoundary = ByteBufMatcher.matcher(concat(TWO_HYPHENS, boundary));
		}

		@Override
		public void onNext(ByteBuf buf) {
			int endIdx = this.firstBoundary.match(buf);
			if (endIdx != -1) {
				if (log.isTraceEnabled()) {
					log.trace("First boundary found @" + endIdx + " in " + toString(buf));
				}
				ByteBuf headers = sliceFrom(buf, endIdx);
				buf.release();

				AbstractState newState = headers(headers.alloc());
				changeState(newState, headers);
			}
			else {
				if (log.isTraceEnabled()) {
					log.trace("Skipping preamble " + toString(buf));
				}
				buf.release();
				requestByteBuf();
			}
		}

		@Override
		public String toString() {
			return "PREAMBLE";
		}
	}

	private static class HeadersState extends AbstractState {

		private static final String HEADER_ENTRY_SEPARATOR = "\\r\\n";

		private final ByteBufMatcher endHeaders = ByteBufMatcher.matcher(concat(CR_LF, CR_LF));

		private final CompositeByteBuf headersBuf;

		private final AtomicBoolean firstBuf = new AtomicBoolean(true);

		public HeadersState(PartGenerator generator, byte[] boundary, ByteBufAllocator allocator) {
			super(generator, boundary);
			this.headersBuf = allocator.compositeBuffer();
		}

		@Override
		public void onNext(ByteBuf buf) {
			if (isLastBoundary(buf)) {
				return;
			}
			int endIdx = this.endHeaders.match(buf);
			if (endIdx != -1) {
				if (log.isTraceEnabled()) {
					log.trace("End of headers found @" + endIdx + " in " + toString(buf));
				}
				this.headersBuf.addComponent(true, sliceTo(buf, endIdx));
				ByteBuf remainder = sliceFrom(buf, endIdx);
				buf.release();

				List<Map.Entry<String, String>> headers = parseHeaders();
				Flux<ByteBuf> bodyFlux = Flux.create(bodySink -> {
					AbstractState body = body(bodySink);
					changeState(body, remainder);
				});
				Part part = new Part(headers, bodyFlux);
				emitPart(part);
			} else {
				if (log.isTraceEnabled()) {
					log.trace("End of headers not found in " + toString(buf));
				}
				this.headersBuf.addComponent(true, buf);
				requestByteBuf();
			}
		}

		private boolean isLastBoundary(ByteBuf buf) {
			if (this.firstBuf.compareAndSet(true, false)) {
				if (buf.readableBytes() >= 2 && buf.getByte(0) == HYPHEN && buf.getByte(1) == HYPHEN) {
					if (log.isTraceEnabled()) {
						log.trace("Last boundary found in " + toString(buf));
					}
					AbstractState completedState = completed();
					changeState(completedState, buf);
					emitComplete();
					return true;
				}
			}
			return false;
		}

		private List<Map.Entry<String, String>> parseHeaders() {
			String string = this.headersBuf.toString(US_ASCII);
			String[] lines = string.split(HEADER_ENTRY_SEPARATOR);
			List<Map.Entry<String, String>> result = new ArrayList<>();
			for (String line : lines) {
				int idx = line.indexOf(':');
				if (idx != -1) {
					String name = line.substring(0, idx);
					String value = line.substring(idx + 1);
					while (value.startsWith(" ")) {
						value = value.substring(1);
					}
					result.add(new AbstractMap.SimpleImmutableEntry<>(name, value));
				}
			}
			this.headersBuf.release();
			if (log.isTraceEnabled()) {
				log.trace("Headers: " + result);
			}
			return result;
		}

		@Override
		public void dispose() {
			if (this.headersBuf.refCnt() > 0) {
				this.headersBuf.release();
			}
		}

		@Override
		public String toString() {
			return "HEADERS";
		}

	}

	private static class BodyState extends AbstractState {

		private final ByteBufMatcher matcher;

		private final FluxSink<ByteBuf> bodySink;

		private final AtomicReference<ByteBuf> previous = new AtomicReference<>();

		public BodyState(PartGenerator generator, byte[] boundary, FluxSink<ByteBuf> bodySink) {
			super(generator, boundary);
			this.matcher = ByteBufMatcher.matcher(concat(CR_LF, TWO_HYPHENS, boundary));
			this.bodySink = bodySink;
			bodySink.onRequest(this::onBodyRequest);
			bodySink.onDispose(this);
		}

		@Override
		public void onNext(ByteBuf buf) {
			int endIdx = this.matcher.match(buf);
			if (endIdx != -1) {
				if (log.isTraceEnabled()) {
					log.trace("Boundary found @" + endIdx + " in " + toString(buf));
				}
				ByteBufAllocator alloc = buf.alloc();
				int len = endIdx - buf.readerIndex() - this.matcher.delimiter().length + 1;
				if (len > 0) {
					// buf contains complete delimiter, let's slice it
					ByteBuf body = buf.retainedSlice(buf.readerIndex(), len);
					push(body);
					push(null);
				} else if (len < 0) {
					// buf starts with the end of the delimiter, let's slice the previous one
					ByteBuf prev = this.previous.get();
					int prevLen = prev.readableBytes() + len;
					if (prevLen == 0) {
						prev.release();
						this.previous.set(null);
					}
					else {
						ByteBuf body = prev.retainedSlice(prev.readerIndex(), prevLen);
						prev.release();
						this.previous.set(body);
						push(null);
					}

				} else /* if (sliceLength == 0) */ {
					// buf starts with complete delimiter
					push(null);
				}

				ByteBuf remainder = sliceFrom(buf, endIdx);
				buf.release();

				AbstractState headersState = headers(alloc);
				changeState(headersState, remainder);
				this.bodySink.complete();
			} else {
				if (log.isTraceEnabled()) {
					log.trace("No boundary found in " + toString(buf));
				}
				push(buf);
				requestByteBuf();
			}
		}

		private void push(ByteBuf buf) {
			if (log.isTraceEnabled()) {
				log.trace("Pushing " + toString(buf));
			}
			ByteBuf previous = this.previous.getAndSet(buf);
			if (previous != null) {
				if (log.isTraceEnabled()) {
					log.trace("Emitting " + toString(previous));
				}
				this.bodySink.next(previous);
			}
		}

		@Override
		public void onComplete() {
			this.bodySink.complete();
			super.onComplete();
		}

		@Override
		public void onError(Throwable throwable) {
			this.bodySink.error(throwable);
			super.onError(throwable);
		}

		@Override
		public void dispose() {
			ByteBuf prev = this.previous.getAndSet(null);
			if (prev != null) {
				if (log.isTraceEnabled()) {
					log.trace("Releasing " + toString(prev));
				}
				prev.release();
			}
		}

		public void onBodyRequest(long n) {
			if (log.isTraceEnabled()) {
				log.trace("Body request: " + n);
			}
		}

		@Override
		public String toString() {
			return "BODY";
		}
	}

	private static class CompletedState extends AbstractState {

		public static final CompletedState INSTANCE = new CompletedState();

		private CompletedState() {
			super(null, null);
		}

		@Override
		public void onNext(ByteBuf buf) {
			if (log.isTraceEnabled()) {
				log.trace("Releasing " + toString(buf));
			}
			buf.release();
		}

		@Override
		public void onError(Throwable throwable) {
			if (log.isWarnEnabled()) {
				log.warn("Received error after completion: ", throwable);
			}
		}

		@Override
		public String toString() {
			return "COMPLETED";
		}
	}


}
