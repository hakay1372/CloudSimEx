package org.cloudbus.cloudsim.ex.geolocation.geoip2;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.cloudbus.cloudsim.ex.geolocation.BaseGeolocationService;
import org.cloudbus.cloudsim.ex.geolocation.IGeolocationService;
import org.cloudbus.cloudsim.ex.geolocation.IPMetadata;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * 
 * A geolocation service that "wraps" another one. It is equiped with a set of
 * rules, which override the resolution rules of the nested service when
 * required.
 * 
 * @author nikolay.grozev
 *
 */
public class GeolocationServiceWithOverrides extends BaseGeolocationService implements IGeolocationService {

    private final IGeolocationService nested;
    private final Set<OverrideRule> rules;

    /** In order to minimise the number rules look-ups. */
    private final Cache<String, OverrideRule> matchedRules = CacheBuilder.newBuilder().concurrencyLevel(1)
            .initialCapacity(INITIAL_CACHE_SIZE).maximumSize(CACHE_SIZE).build();
    /** In order to minimise the number rules look-ups. */
    private final Cache<String, Boolean> nonMatchedIps = CacheBuilder.newBuilder().concurrencyLevel(1)
            .initialCapacity(INITIAL_CACHE_SIZE).maximumSize(CACHE_SIZE).build();

    /**
     * Ctor.
     * 
     * @param nested - the service to delegate to. Must not be null.
     * @param rules - the rules, used to override the nested service. Must not be null or empty.
     */
    public GeolocationServiceWithOverrides(final IGeolocationService nested, final Collection<OverrideRule> rules) {
        Preconditions.checkNotNull(nested);
        Preconditions.checkNotNull(rules);
        Preconditions.checkArgument(!rules.isEmpty());
        this.nested = nested;
        this.rules = Collections.unmodifiableSet(new LinkedHashSet<OverrideRule>(rules));
    }
    
    private OverrideRule getRule(final String ip) {
        OverrideRule rule = matchedRules.getIfPresent(ip);
        // If not in cache
        if (rule == null) {
            Boolean nonMatched = nonMatchedIps.getIfPresent(ip);
            // If not matched yet and previously unseen
            if (nonMatched == null) {
                Logger l = Logger.getLogger(getClass().getCanonicalName());
                l.warning("\nScanning for: " + ip + "\n\n");
                for (OverrideRule overrideRule : rules) {
                    if (overrideRule.matches(ip)) {
                        rule = overrideRule;
                        break;
                    }
                }

                // Update the caches
                if (rule != null) {
                    matchedRules.put(ip, rule);
                } else {
                    nonMatchedIps.put(ip, false);
                }

            }
        }
        return rule;
    }

    @Override
    public double[] getCoordinates(String ip) {
        OverrideRule rule = getRule(ip);
        if (rule != null) {
            return new double[] { rule.getLat(), rule.getLon() };
        } else {
            return nested.getCoordinates(ip);
        }
    }

    @Override
    public IPMetadata getMetaData(String ip) {
        OverrideRule rule = getRule(ip);
        if (rule != null) {
            return new IPMetadata("N/A", "N/A", "N/A", "N/A", "N/A", rule.getLocation(), rule.getLat(), rule.getLon());
        } else {
            return nested.getMetaData(ip);
        }
    }

    @Override
    public double latency(String ip1, String ip2) {
        return nested.latency(getCoordinates(ip1), getCoordinates(ip2));
    }

    @Override
    public void close() throws IOException {
        nested.close();
    }

    @Override
    public double latency(double[] reqCoord1, double[] reqCoord2) {
        return nested.latency(reqCoord1, reqCoord2);
    }
}
