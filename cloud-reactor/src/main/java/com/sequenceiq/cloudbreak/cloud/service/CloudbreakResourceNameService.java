package com.sequenceiq.cloudbreak.cloud.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

public abstract class CloudbreakResourceNameService {

    public static final String DELIMITER = "-";

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudbreakResourceNameService.class);

    private static final int MAX_PART_LENGTH = 20;

    private static final String DATE_FORMAT = "yyyyMMddHHmmss";

    public String trimHash(String part) {
        if (part == null) {
            throw new IllegalStateException("Resource name part must not be null!");
        }
        LOGGER.debug("Trim the hash part from the end: {}", part);
        String[] parts = part.split(DELIMITER);
        String trimmed = part;
        try {
            trimmed = StringUtils.collectionToDelimitedString(Arrays.asList(Arrays.copyOf(parts, parts.length - 1)), DELIMITER);
        } catch (NumberFormatException ignored) {
            LOGGER.debug("No need to trim hash: {}", part);
        }

        return trimmed;
    }

    public String adjustPartLength(String part) {
        return adjustPartLength(part, MAX_PART_LENGTH);
    }

    public String adjustPartLength(String part, int maxLength) {
        if (part == null) {
            throw new IllegalStateException("Resource name part must not be null!");
        }
        String shortPart = part;
        if (part.length() > maxLength) {
            LOGGER.debug("Shortening part name: {}", part);
            shortPart = String.copyValueOf(part.toCharArray(), 0, maxLength);
        } else {
            LOGGER.debug("Part name length OK, no need to shorten: {}", part);
        }
        return shortPart;
    }

    protected String adjustBaseLength(String base, int platformSpecificLength) {
        if (base == null) {
            throw new IllegalStateException("Name must not be null!");
        }
        if (base.length() > platformSpecificLength) {
            LOGGER.debug("Shortening name: {}", base);
            List<String> splitedBase = Splitter.on("-").splitToList(base);
            String stackName;
            String instanceName;
            if ((splitedBase.get(0).length() - (base.length() - platformSpecificLength)) > 1) {
                stackName = Splitter.fixedLength(splitedBase.get(0).length() - (base.length() - platformSpecificLength))
                        .splitToList(splitedBase.get(0)).get(0);
                instanceName = splitedBase.get(1);
            } else {
                stackName = Splitter.fixedLength(1).splitToList(splitedBase.get(0)).get(0);
                instanceName = Splitter.fixedLength(splitedBase.get(1).length()
                        - (Math.abs(splitedBase.get(0).length() - (base.length() - platformSpecificLength))
                        + stackName.length())).splitToList(splitedBase.get(1)).get(0);
            }
            StringBuilder shortBase = new StringBuilder(stackName + DELIMITER + instanceName);
            for (int i = 2; i < splitedBase.size(); i++) {
                shortBase.append(DELIMITER).append(splitedBase.get(i));
            }
            return shortBase.toString();
        } else {
            LOGGER.debug("Name length OK, no need to shorten: {}", base);
            return base;
        }
    }

    public String normalize(Long part) {
        return normalize(String.valueOf(part));
    }

    public String normalize(String part) {
        if (part == null) {
            throw new IllegalStateException("Resource name part must not be null!");
        }
        LOGGER.debug("Normalizing resource name part: {}", part);

        String normalized = StringUtils.trimAllWhitespace(part);
        LOGGER.debug("Trimmed whitespaces: {}", part);

        normalized = normalized.replaceAll("[^a-zA-Z0-9]", "");
        LOGGER.debug("Trimmed invalid characters: {}", part);

        normalized = normalized.toLowerCase();
        LOGGER.debug("Lower case: {}", part);

        return normalized;
    }

    protected String appendPart(String base, String part) {
        if (null == base && null == part) {
            throw new IllegalArgumentException("base and part are both null! Can't append them!");
        }
        StringBuilder sb = null != base ? new StringBuilder(base).append(DELIMITER).append(part) : new StringBuilder(part);
        return sb.toString();
    }

    protected String appendPart(String base, Long part) {
        return appendPart(base, String.valueOf(part));
    }

    protected String appendPart(String base, int part) {
        return appendPart(base, String.valueOf(part));
    }

    protected String appendHash(String name, Date timestamp) {
        DateFormat df = new SimpleDateFormat(DATE_FORMAT);
        return Joiner.on("").join(name, DELIMITER, df.format(timestamp));
    }

    protected String appendHash(String name, Long value) {
        return appendHash(name, String.valueOf(value));
    }

    protected int getDefaultHashLength() {
        return DATE_FORMAT.length();
    }

    protected String appendHash(String name, String toBeHashed) {
        return appendHash(name, toBeHashed, getDefaultHashLength());
    }

    protected String appendHash(String name, String toBeHashed, int length) {
        return Joiner.on("").join(name, DELIMITER, DigestUtils.md5DigestAsHex(String.valueOf(toBeHashed).getBytes()).substring(0, length));
    }
}
