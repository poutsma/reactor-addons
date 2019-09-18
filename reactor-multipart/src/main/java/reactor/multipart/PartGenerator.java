package reactor.multipart;

import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.FluxSink;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * @author Arjen Poutsma
 */
class PartGenerator extends BaseSubscriber<ByteBuf> {

	static final Logger log = Loggers.getLogger(PartGenerator.class);

	private final AtomicReference<State> state;

	private final FluxSink<Part> partSink;

	PartGenerator(FluxSink<Part> partSink, byte[] boundary) {
		this.partSink = partSink;
		this.state = new AtomicReference<>(AbstractState.preamble(this, boundary));
	}

	@Override
	protected void hookOnSubscribe(Subscription subscription) {
		if (log.isTraceEnabled()) {
			log.trace("Subscribe: " + subscription);
		}
		this.state.get().onSubscribe(subscription);
	}

	@Override
	protected void hookOnNext(ByteBuf value) {
		if (log.isTraceEnabled()) {
			log.trace("Next: " + AbstractState.toString(value));
		}
		this.state.get().onNext(value);
	}

	@Override
	protected void hookOnComplete() {
		if (log.isTraceEnabled()) {
			log.trace("Completed");
		}
		this.state.get().onComplete();
	}

	@Override
	protected void hookOnError(Throwable throwable) {
		if (log.isTraceEnabled()) {
			log.trace("Error: " + throwable);
		}
		this.state.get().onError(throwable);
	}

	public void onRequest(long n) {
		if (log.isTraceEnabled()) {
			log.trace("Part request: " + n);
		}
		this.state.get().onRequest(n);
	}
	
	public void onCancel() {
		if (log.isTraceEnabled()) {
			log.trace("Cancel");
		}
		cancel();
		this.state.get().onCancel();
	}

	public void emitPart(Part part) {
		this.partSink.next(part);
	}

	public void emitError(Throwable t) {
		this.partSink.error(t);
	}

	public void emitComplete() {
		this.partSink.complete();
	}

	public void changeState(State newState) {
		State oldState = this.state.getAndSet(newState);
		if (log.isTraceEnabled()) {
			log.trace("Changed state: " + oldState + " -> " + newState);
		}
		if (oldState instanceof Disposable) {
			((Disposable) oldState).dispose();
		}
	}

}
