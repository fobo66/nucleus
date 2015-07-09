package nucleus.presenter;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;

import rx.Notification;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.internal.util.SubscriptionList;
import rx.subjects.AsyncSubject;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * This is an extension of {@link nucleus.presenter.Presenter} which provides RxJava functionality.
 *
 * @param <ViewType> a type of view
 */
public class RxPresenter<ViewType> extends Presenter<ViewType> {

    private static final String REQUESTED_KEY = RxPresenter.class.getName() + "#requested";

    private ArrayList<Integer> requested = new ArrayList<>();
    private HashMap<Integer, Func0<Subscription>> factories = new HashMap<>();
    private HashMap<Integer, Subscription> restartableSubscriptions = new HashMap<>();

    private BehaviorSubject<ViewType> view = BehaviorSubject.create();
    private SubscriptionList subscriptions = new SubscriptionList();

    /**
     * Returns an observable that emits current status of a view.
     * True - a view is attached, False - a view is detached.
     *
     * @return an observable that emits current status of a view.
     */
    public Observable<ViewType> view() {
        return view;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null)
            requested = savedState.getIntegerArrayList(REQUESTED_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Subscription subs : restartableSubscriptions.values())
            subs.unsubscribe();
        view.onCompleted();
        subscriptions.unsubscribe();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSave(Bundle state) {
        super.onSave(state);
        for (int i = requested.size() - 1; i >= 0; i--) {
            Integer restartableId = requested.get(i);
            if (restartableSubscriptions.get(restartableId).isUnsubscribed()) {
                requested.remove(i);
                restartableSubscriptions.remove(restartableId);
            }
        }
        state.putIntegerArrayList(REQUESTED_KEY, requested);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onTakeView(ViewType view) {
        super.onTakeView(view);
        this.view.onNext(view);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDropView() {
        super.onDropView();
        view.onNext(null);
    }

    /**
     * A restartable is any RxJava query/request that can be started (subscribed) and
     * should be automatically restarted (re-subscribed) after a process restart.
     * <p/>
     * Registers a factory for a restartable. Re-subscribes the restartable after a process restart.
     *
     * @param restartableId id of a restartable.
     * @param factory       a factory that will create an actual rxjava subscription when requested.
     */
    public void registerRestartable(int restartableId, Func0<Subscription> factory) {
        factories.put(restartableId, factory);
        if (requested.contains(restartableId))
            restartableSubscriptions.put(restartableId, factories.get(restartableId).call());
    }

    /**
     * Subscribes (runs) a restartable using a factory method provided with {@link #registerRestartable}.
     * If a presenter gets lost during a process restart while a restartable is still
     * subscribed, the restartable will be restarted on next {@link #registerRestartable} call.
     * <p/>
     * If the restartable is already subscribed then it will be unsubscribed first.
     * <p/>
     * The restartable will be unsubscribed during {@link #onDestroy()}
     *
     * @param restartableId id of a restartable.
     */
    public void subscribeRestartable(int restartableId) {
        unsubscribeRestartable(restartableId);
        requested.add(restartableId);
        restartableSubscriptions.put(restartableId, factories.get(restartableId).call());
    }

    /**
     * Unsubscribes a restartable
     *
     * @param restartableId id of a restartable.
     */
    public void unsubscribeRestartable(int restartableId) {
        if (restartableSubscriptions.containsKey(restartableId)) {
            restartableSubscriptions.get(restartableId).unsubscribe();
            restartableSubscriptions.remove(restartableId);
        }
        requested.remove((Integer)restartableId);
    }

    /**
     * Registers a subscription to automatically unsubscribe it during onDestroy.
     * See {@link SubscriptionList#add(Subscription) for details.}
     *
     * @param subscription a subscription to add.
     */
    public void add(Subscription subscription) {
        subscriptions.add(subscription);
    }

    /**
     * Removes and unsubscribes a subscription that has been registered with {@link #add} previously.
     * See {@link SubscriptionList#remove(Subscription) for details.}
     *
     * @param subscription a subscription to remove.
     */
    public void remove(Subscription subscription) {
        subscriptions.remove(subscription);
    }

    /**
     * Returns a transformer that will delay onNext, onError and onComplete emissions unless a view become available.
     * getView() is guaranteed to be != null during all emissions. This transformer can only be used on application's main thread.
     * <p/>
     * Use this operator if you need to deliver *all* emissions to a view, in example when you're sending items
     * into adapter one by one.
     *
     * @param <T> a type of onNext value.
     * @return the delaying operator.
     */
    public <T> Observable.Transformer<T, Delivery<T>> deliver(final DeliveryRule rule) {
        return new DeliveryTransformer<>(rule);
    }

    public class DeliveryTransformer<T> implements Observable.Transformer<T, Delivery<T>> {

        private DeliveryRule rule;

        public DeliveryTransformer(DeliveryRule rule) {
            this.rule = rule;
        }

        @Override
        public Observable<Delivery<T>> call(Observable<T> observable1) {
            Subject<T, T> subject = AsyncSubject.create();

            final Observable<T> source = rule == DeliveryRule.CACHE ? subject.cache() :
                rule == DeliveryRule.REPLAY ? subject.replay() : subject;

            final Subscription subscription = observable1.subscribe(subject);

            return view
                .switchMap(new Func1<ViewType, Observable<Delivery<T>>>() {
                    @Override
                    public Observable<Delivery<T>> call(final ViewType view) {
                        return view == null ? Observable.<Delivery<T>>empty() :
                            source.materialize()
                                .map(new Func1<Notification<T>, Delivery<T>>() {
                                    @Override
                                    public Delivery<T> call(Notification<T> t) {
                                        return new Delivery<>(view, t);
                                    }
                                });
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        subscription.unsubscribe();
                    }
                });
        }
    }

    public enum DeliveryRule {PUBLISH, CACHE, REPLAY}

    public class Delivery<T> {
        final ViewType view;
        final Notification<T> notification;

        private Delivery(ViewType view, Notification<T> notification) {
            this.view = view;
            this.notification = notification;
        }

        public void split(Action2<ViewType, T> onNext, Action2<ViewType, Throwable> onError) {
            if (notification.getKind() == Notification.Kind.OnNext)
                onNext.call(view, notification.getValue());
            else if (notification.getKind() == Notification.Kind.OnError)
                onError.call(view, notification.getThrowable());
        }
    }

    public class Deliver<T> implements Observer<T> {

        private Subject<Notification<T>, Notification<T>> subject;
        private final Subscription viewSubscription;

        public Deliver(DeliveryRule rule, final Action2<ViewType, T> onNext, final Action2<ViewType, Throwable> onError) {
            this.subject = PublishSubject.create();

            final Observable<Notification<T>> source = rule == DeliveryRule.CACHE ? subject.cache() :
                rule == DeliveryRule.REPLAY ? subject.replay() : subject;

            viewSubscription = view
                .switchMap(new Func1<ViewType, Observable<Delivery<T>>>() {
                    @Override
                    public Observable<Delivery<T>> call(final ViewType view) {
                        return view == null ? Observable.<Delivery<T>>empty() :
                            source.map(new Func1<Notification<T>, Delivery<T>>() {
                                @Override
                                public Delivery<T> call(Notification<T> t) {
                                    return new Delivery<>(view, t);
                                }
                            });
                    }
                })
                .subscribe(new Action1<Delivery<T>>() {
                    @Override
                    public void call(Delivery<T> tDelivery) {
                        tDelivery.split(onNext, onError);
                    }
                });
        }

        @Override
        public void onCompleted() {
            subject.onNext(Notification.<T>createOnCompleted());
        }

        @Override
        public void onError(Throwable e) {
            subject.onNext(Notification.<T>createOnError(e));
        }

        @Override
        public void onNext(T t) {
            subject.onNext(Notification.createOnNext(t));
        }
    }

    public class DeliverDelivery<T> implements Observer<Delivery<T>> {

        private Action2<ViewType, T> onNext;
        private Action2<ViewType, Throwable> onError;

        public DeliverDelivery(final Action2<ViewType, T> onNext, final Action2<ViewType, Throwable> onError) {
            this.onNext = onNext;
            this.onError = onError;
        }

        @Override
        public void onCompleted() {
        }

        @Override
        public void onError(Throwable e) {
        }

        @Override
        public void onNext(Delivery<T> tDelivery) {
            tDelivery.split(onNext, onError);
        }
    }
}
