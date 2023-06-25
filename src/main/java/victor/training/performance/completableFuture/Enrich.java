package victor.training.performance.completableFuture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.With;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class Enrich {
    @AllArgsConstructor
    protected static class A {
        public final String a;
    }

    @AllArgsConstructor
    protected static class B {
        public final String b;
    }

    @AllArgsConstructor
    protected static class C {
        public final String c;
    }

    @Value
    protected static class AB {
        public A a;
        public B b;
    }

    @Value
    protected static class ABC {
        public A a;
        public B b;
        public C c;
    }

    protected interface Dependency {
        CompletableFuture<A> a(int id);

        CompletableFuture<B> b(int id);

        CompletableFuture<B> b1(A a);

        CompletableFuture<C> c(int id);

        CompletableFuture<C> c1(A a);

        CompletableFuture<C> c2(A a, B b);

        CompletableFuture<A> saveA(A a);

        CompletableFuture<Void> auditA(A a1, A a0);
    }

    protected final Dependency dependency;

    public Enrich(Dependency dependency) {
        this.dependency = dependency;
    }

    //  😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇 😇
    // ⚠️ ATTENTION ⚠️ ENTERING HEAVEN
    //
    // ALL THE NETWORK CALLS HAVE BEEN ALREADY CAREFULLY WRAPPED IN NON-BLOCKING FUNCTIONS
    //   eg. relying on WebClient or reactive drivers (R2DBC, reactive kafka, cassandra)
    // NO FUNCTION EVER BLOCKS ANY THREAD ANYMORE
    // ********************************


    // ==================================================================================================

    /**
     * a(id) || b(id) ==> AB(a,b)
     */
    public CompletableFuture<AB> p01_a_par_b(int id) {
        return dependency.a(id).thenCombine(dependency.b(id), AB::new);
    }

    // ==================================================================================================

    /**
     * a(id), then b1(a) ==> AB(a,b)
     */
    public CompletableFuture<AB> p02_a_then_b1(int id) {
        return dependency.a(id).thenCompose(a -> dependency.b1(a).thenApply(b -> new AB(a, b)));
    }

    // ==================================================================================================

    /**
     * a(id), then b1(a) || c1(a) ==> ABC(a,b,c)
     */
    public CompletableFuture<ABC> p03_a_then_b1_par_c1(int id) {
        return dependency.a(id).thenCompose(a -> dependency.b1(a).thenCombine(dependency.c1(a), (b, c) -> new ABC(a, b, c)));
    }

    // ==================================================================================================

    /**
     * a(id) || b(id) || c(id) ==> ABC(a,b,c)
     */
    public CompletableFuture<ABC> p04_a_b_c(int id) {
        CompletableFuture<A> futureA = dependency.a(id);
        CompletableFuture<B> futureB = dependency.b(id);
        CompletableFuture<C> futureC = dependency.c(id);
        return CompletableFuture.allOf(futureA, futureB, futureC)
                .thenApply(v -> new ABC(futureA.join(), futureB.join(), futureC.join()));
    }

    // ==================================================================================================

    /**
     * a(id), then b1(a), then c2(a,b) ==> ABC(a,b,c)
     */
    public CompletableFuture<ABC> p05_a_then_b1_then_c2(int id) {
        return dependency.a(id)
                .thenCompose(a -> dependency.b1(a)
                        .thenApply(b -> new AB(a, b)))
                .thenCompose(ab -> dependency.c2(ab.a, ab.b)
                        .thenApply(c -> new ABC(ab.a, ab.b, c)));
    }

    // ==================================================================================================

    /**
     * a0 = a(id), b0 = b1(a0), c0 = c1(a0)
     * --
     * a1=logic(a0,b0,c0)
     * --
     * saveA(a1)
     * trackA(a1,a0) ;
     * <p>
     * [HARD] the flow should NOT wait for trackA() to complete; but any errors occurred in trackA() should be logged
     * Play: propagate an Immutable Context
     */
    public CompletableFuture<Void> p06_complexFlow(int id) throws ExecutionException, InterruptedException {
        return dependency.a(id)
                .thenApply(a -> new MyContext().withA(a))

                // *** sequential
                // .thenCompose(context -> dependency.b1(context.a).thenApply(context::withB)
                // .thenCompose(context -> dependency.c1(context.a).thenApply(context::withC))

                // *** parallel
                .thenCompose(context->
                        dependency.b1(context.a).thenCombine(
                                dependency.c1(context.a), (b,c)->context.withB(b).withC(c)))

                .thenApply(context -> context.withA1(logic(context.a, context.b, context.c)))

                .thenCompose(context -> dependency.saveA(context.a1).thenApply(a -> context))

                .whenComplete((context, e__) -> {
                    if (context != null) dependency.auditA(context.a1, context.a)
                            .whenComplete((v, err)-> {
                                if (err != null) {
                                    err.printStackTrace();
                                }
                            });
                })
                .thenApply(context -> null);
    }

    public A logic(A a, B b, C c) {
        A a1 = new A(a.a + b.b.toUpperCase() + c.c);
        return a1;
    }

    @Data
    @AllArgsConstructor
    @With
    private static class MyContext {
        public final A a;
        public final B b;
        public final C c;
        public final A a1;
        public MyContext() {
            this(null, null, null, null);
        }
    }
}
