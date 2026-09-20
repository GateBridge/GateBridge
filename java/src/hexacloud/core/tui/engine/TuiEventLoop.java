package hexacloud.core.tui.engine;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import hexacloud.core.tui.TerminalUI;
import hexacloud.core.utils.concurrent.ThreadManager;

/**
 * Single-threaded event reactor for processing UI events in sequence.
 * Enforces single-threaded state mutations and deterministic event handling.
 */
public class TuiEventLoop implements Runnable {

    private final LinkedBlockingQueue<UIEvent> eventQueue = new LinkedBlockingQueue<>();
    private final TerminalUI tui;
    private Consumer<UIEvent> customHandler;
    private volatile boolean running = false;
    private Thread loopThread;

    public TuiEventLoop(TerminalUI tui) {
        this.tui = tui;
    }

    public TuiEventLoop(TerminalUI tui, Consumer<UIEvent> customHandler) {
        this.tui = tui;
        this.customHandler = customHandler;
    }

    public void setCustomHandler(Consumer<UIEvent> customHandler) {
        this.customHandler = customHandler;
    }

    /**
     * Post a UIEvent to the single-threaded reactor queue.
     */
    public void postEvent(UIEvent event) {
        if (event != null) {
            eventQueue.offer(event);
        }
    }

    /**
     * Synchronously process a single event from the queue.
     * Useful for deterministic unit testing.
     *
     * @return true if an event was dequeued and processed, false if the queue was empty.
     */
    public boolean processOne() {
        UIEvent event = eventQueue.poll();
        if (event == null) {
            return false;
        }
        dispatch(event);
        return true;
    }

    public int getQueueSize() {
        return eventQueue.size();
    }

    public boolean isQueueEmpty() {
        return eventQueue.isEmpty();
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Main event reactor loop. Continually takes events from queue and dispatches them.
     */
    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                UIEvent event = eventQueue.take();
                if (event instanceof UIEvent.ShutdownEvent) {
                    running = false;
                    break;
                }
                dispatch(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
                break;
            } catch (Throwable t) {
                // Prevent event loop crash on unhandled handler error
            }
        }
    }

    /**
     * Start the event loop on a virtual thread.
     */
    public void start() {
        if (running) return;
        loopThread = ThreadManager.startVirtual("TuiEventLoopThread", this);
    }

    /**
     * Stop the event loop.
     */
    public void stop() {
        running = false;
        postEvent(new UIEvent.ShutdownEvent());
        if (loopThread != null && loopThread.isAlive()) {
            loopThread.interrupt();
        }
    }

    /**
     * Offload heavy or blocking I/O asynchronously via Virtual Threads and post completion event back to queue.
     */
    public void submitAsync(Runnable blockingTask, Runnable completionCallback) {
        ThreadManager.startVirtual("TuiAsyncWorker", () -> {
            try {
                if (blockingTask != null) {
                    blockingTask.run();
                }
            } finally {
                if (completionCallback != null) {
                    postEvent(new UIEvent.ActionEvent("ASYNC_COMPLETE", completionCallback));
                } else {
                    postEvent(new UIEvent.RedrawEvent(true));
                }
            }
        });
    }

    /**
     * Dispatch an event to state / renderer handlers.
     */
    public void dispatch(UIEvent event) {
        if (event == null) return;

        if (customHandler != null) {
            customHandler.accept(event);
        }

        if (tui == null) return;

        if (event instanceof UIEvent.KeyPressEvent) {
            UIEvent.KeyPressEvent kp = (UIEvent.KeyPressEvent) event;
            synchronized (tui.state()) {
                tui.keyHandler().handleKeyPress(kp.getKey());
            }
            tui.triggerRedraw(true);
        } else if (event instanceof UIEvent.RedrawEvent) {
            UIEvent.RedrawEvent re = (UIEvent.RedrawEvent) event;
            tui.triggerRedraw(re.isImmediate());
        } else if (event instanceof UIEvent.ResizeEvent) {
            tui.triggerRedraw(true);
        } else if (event instanceof UIEvent.ActionEvent) {
            UIEvent.ActionEvent ae = (UIEvent.ActionEvent) event;
            if (ae.getPayload() instanceof Runnable) {
                ((Runnable) ae.getPayload()).run();
            }
        } else if (event instanceof UIEvent.ShutdownEvent) {
            tui.state().running = false;
        }
    }
}
