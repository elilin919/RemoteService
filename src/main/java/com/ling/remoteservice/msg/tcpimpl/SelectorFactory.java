package com.ling.remoteservice.msg.tcpimpl;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Factory used to dispatch/share {@link Selector}.
 *
 * @author Scott Oaks
 * @author Jean-Francois Arcand
 * @author gustav trede
 */
public class SelectorFactory {
	static Log logger=LogFactory.getLog(SelectorFactory.class);
	
    public static final int DEFAULT_MAX_SELECTORS = 20;
    /**
     * The number of {@link Selector} to create.
     */
    private static volatile int maxSelectors = DEFAULT_MAX_SELECTORS;
    /**
     * Cache of {@link Selector}
     */
    private final static ConcurrentQueue<Selector> selectors =
            new ConcurrentQueue<Selector>("temporary-selectors-queue");
    /**
     * have we created the Selector instances.
     */
    private static volatile boolean initialized = false;

    // selector poll timeout
    private static volatile long timeout = Long.MAX_VALUE;
    
    /**
     * Set max selector pool size.
     * @param size max pool size
     */
    public static void setMaxSelectors(int size) throws IOException {
        synchronized (selectors) {
            if (size < 0) {
                logger.warn(" tried to remove too many selectors " +
                        size +">=" + maxSelectors, new Exception());
                return;
            }
            int toAdd = initialized ? size - maxSelectors : size;
            if (toAdd > 0) {
                while (toAdd-- > 0) {
                    selectors.add(createSelector());
                }
            } else {
                reduce(-toAdd);
            }
            maxSelectors = size;
            initialized = true;
        }
    }

    /**
     * Changes the Selector cache size
     * @param delta
     * @throws IOException
     */
    public static void changeSelectorsBy(int delta) throws IOException {
        synchronized (selectors) {
            setMaxSelectors(maxSelectors + delta);
        }
    }

    /**
     * Returns max selector pool size
     * @return max pool size
     */
    public static int getMaxSelectors() {
        return maxSelectors;
    }

    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Get a exclusive {@link Selector}
     * @return {@link Selector}
     */
    public static Selector getSelector() {
        if (!initialized) {
            try {
                setMaxSelectors(maxSelectors);
            } catch (IOException ex) {
            	logger.warn(
                        "static init of SelectorFactory failed", ex);
            }
        }

        Selector selector = null;
        try {
            selector = selectors.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        	logger.info("Interrupted during selector polling", e);
        }
        
        if (selector == null) {
        	logger.warn(
                    "No Selector available. Increase default: " + maxSelectors);
        }
        return selector;
    }

    /**
     * Please ensure to use try finally around get and return of selector so avoid leaks.
     * Return the {@link Selector} to the cache
     * @param s {@link Selector}
     */
    public static void returnSelector(Selector s) {
        selectors.offer(s);
    }

    /**
     * Executes <code>Selector.selectNow()</code> and returns
     * the {@link Selector} to the cache
     */
    public static void selectNowAndReturnSelector(Selector s) {
        try {
            s.selectNow();
            returnSelector(s);
        } catch (IOException e) {
        	logger.warn(
                    "Unexpected problem when releasing temporary Selector", e);
            try {
                s.close();
            } catch (IOException ee) {
                // We are not interested
            }

            try {
                reimburseSelector();
            } catch (IOException ee) {
            	logger.warn(
                        "Problematic Selector could not be reimbursed!", ee);
            }
        }
    }

    /**
     * Add Selector to the cache.
     * This method could be called to reimberse a lost or problematic Selector.
     *
     * @throws IOException
     */
    public static void reimburseSelector() throws IOException {
        returnSelector(createSelector());
    }

    /**
     * Decrease {@link Selector} pool size
     */
    private static void reduce(int tokill) {
        while (tokill-- > 0) {
            try {
                Selector selector = selectors.poll();
                if (selector != null) {
                    selector.close();
                } else {
                    // can happen in concurrent usage, if selectors are in use and hence not in cache.
                	logger.warn("SelectorFactory cache could " +
                            "not remove the desired number, too few selectors in cache.");
                    return;
                }
            } catch (IOException e) {
                if (logger.isInfoEnabled()) {
                    logger.info("SelectorFactory.reduce", e);
                }
            }
        }
    }

    /**
     * Creeate Selector
     * @return Selector
     * @throws java.io.IOException
     */
    protected static Selector createSelector() throws IOException {
        return Selector.open();
    }

    public static long getTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    public static void setTimeout(long timeout, TimeUnit timeUnit) {
        SelectorFactory.timeout = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }
}