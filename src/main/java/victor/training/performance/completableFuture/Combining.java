package victor.training.performance.completableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Combining {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected interface Dependency {
        CompletableFuture<String> call();

        CompletableFuture<Void> task(String s);

        CompletableFuture<Integer> parseIntRemotely(String s);

        void cleanup();

        CompletableFuture<Integer> fetchAge();

        CompletableFuture<Void> audit(String s);
    }

    protected final Dependency dependency;

    public Combining(Dependency dependency) {
        this.dependency = dependency;
    }


    // ==================================================================================================

    /**
     * Return the uppercase of the future value, without blocking (.get() or .join()).
     */
    public CompletableFuture<String> p01_transform() {
        return dependency.call().thenApply(String::toUpperCase);
    }

    // ==================================================================================================

    /**
     * Run dependency#task(s); After it completes, call dependency#cleanup();
     * Hint: completableFuture.then....
     */
    public void p02_chainRun(String s) {
        dependency.task(s).thenRun(dependency::cleanup);
    }

    // ==================================================================================================

    /**
     * Run dependency#task(s) passing the string returned by the dependency#call(). Do not block (get/join)!
     */
    public void p03_chainConsume() throws InterruptedException, ExecutionException {
        dependency.call().thenAccept(dependency::task);
    }

    // ==================================================================================================

    /**
     * Launch #call(); when it completes, call #parseIntRemotely(s) with the result,
     * and return the parsed int.
     */
    public CompletableFuture<Integer> p04_chainFutures() throws ExecutionException, InterruptedException {
        return dependency.call().thenCompose(dependency::parseIntRemotely);

    }

    // ==================================================================================================

    /**
     * Same as previous, but return a CompletableFuture< Void > to let the caller:
     * (a) know when the task finished, and/or
     * (b) find out of any exceptions
     */
    public CompletableFuture<Void> p05_chainFutures_returnFutureVoid() throws ExecutionException, InterruptedException {
        return dependency.call().thenCompose(dependency::task);
    }


    // ==================================================================================================

    /**
     * Launch #call; when it completes launch #task and #cleanup;
     * After both complete, complete the returned future.
     * Reminder: Don't block! (no .get or .join) in the entire workshop!
     * Bonus: try to run #task() and #cleanup() in parallel (log.info prints the thread name) Hint: ...Async(
     */
    public CompletableFuture<Void> p06_all() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = dependency.call();
        return CompletableFuture.allOf(future.thenComposeAsync(dependency::task), future.thenRunAsync(dependency::cleanup));
    }

    // ==================================================================================================

    /**
     * Launch #call and #fetchAge. When BOTH complete, combine their values like so:
     * callResult + " " + ageResult
     * and complete the returned future with this value. Don't block.
     */
    public CompletableFuture<String> p07_combine() {
        return dependency.call().thenCombine(dependency.fetchAge(), (r1, r2) -> r1 + " " + r2);
    }

    // ==================================================================================================

    /**
     * === The contest ===
     * Launch #call and #fetchAge in parallel.
     * The value of the FIRST to complete (ignore the other),
     * converted to string, should be used to complete the returned future.
     * Hint: thenCombine waits for all to complete. - Not good
     * Hint#2: Either... or anyOf()
     * -- after solving Exceptions.java  --
     * [HARD⭐️] if the first completes with error, wait for the second.
     * [HARD⭐️⭐️⭐️] If both in error, complete in error.
     */
    public CompletableFuture<String> p08_fastest() {
        return CompletableFuture.anyOf(dependency.call(), dependency.fetchAge()).thenApply(Object::toString);
    }

    // ==================================================================================================

    /**
     * Launch #call(); When it completes, call #audit() with the value and then return it.
     * ⚠️ Don't wait for #audit() to complete (useful in case it takes time)
     * ⚠️ If #audit() fails, ignore that error (don't fail the returned future), but also log the error!
     */
    public CompletableFuture<String> p09_fireAndForget() throws ExecutionException, InterruptedException {
        return dependency.call()
                .whenComplete((s, err) -> {
                    if (s != null) {
                        dependency.audit(s).exceptionally(e -> {
                            System.out.println(e);
                            return null;
                        });
                    }
                    ;
                });
    }

}
