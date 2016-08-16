package feign.hystrix;

import feign.FeignException;
import feign.RequestLine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static feign.assertj.MockWebServerAssertions.assertThat;

public class FallbackFactoryTest {

  interface TestInterface {
    @RequestLine("POST /") String invoke();
  }

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void fallbackFactory_example() {
    server.enqueue(new MockResponse().setResponseCode(500));
    server.enqueue(new MockResponse().setResponseCode(404));

    TestInterface api = target(cause -> () -> {
      assertThat(cause).isInstanceOf(FeignException.class);
      return ((FeignException) cause).status() == 500 ? "foo" : "bar";
    });

    assertThat(api.invoke()).isEqualTo("foo");
    assertThat(api.invoke()).isEqualTo("bar");
  }

  @Test
  public void defaultFallbackFactory_delegates() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target(new FallbackFactory.Default<>(() -> "foo"));

    assertThat(api.invoke())
        .isEqualTo("foo");
  }

  @Test
  public void defaultFallbackFactory_doesntLogByDefault() {
    server.enqueue(new MockResponse().setResponseCode(500));

    Logger logger = new Logger("", null) {
      @Override public void log(Level level, String msg, Throwable thrown) {
        throw new AssertionError("logged eventhough not FINE level");
      }
    };

    target(new FallbackFactory.Default<>(() -> "foo", logger)).invoke();
  }

  @Test
  public void defaultFallbackFactory_logsAtFineLevel() {
    server.enqueue(new MockResponse().setResponseCode(500));

    AtomicBoolean logged = new AtomicBoolean();
    Logger logger = new Logger("", null) {
      @Override public void log(Level level, String msg, Throwable thrown) {
        logged.set(true);

        assertThat(msg).isEqualTo("fallback due to: status 500 reading TestInterface#invoke()");
        assertThat(thrown).isInstanceOf(FeignException.class);
      }
    };
    logger.setLevel(Level.FINE);

    target(new FallbackFactory.Default<>(() -> "foo", logger)).invoke();
    assertThat(logged.get()).isTrue();
  }

  TestInterface target(FallbackFactory<TestInterface> factory) {
    return HystrixFeign.builder()
        .target(TestInterface.class, "http://localhost:" + server.getPort(), factory);
  }
}
