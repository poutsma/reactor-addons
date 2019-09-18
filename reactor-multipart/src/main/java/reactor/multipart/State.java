package reactor.multipart;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Subscription;

/**
 * @author Arjen Poutsma
 */
interface State {

	void onSubscribe(Subscription subscription);

	void onNext(ByteBuf buf);

	void onComplete();

	void onError(Throwable throwable);

	void onRequest(long n);

	void onCancel();
}
