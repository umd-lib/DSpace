/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

/**
 * The Bitstream REST Resource
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 */
@LinksRest(links = {
    @LinkRest(name = BitstreamRest.BUNDLE, method = "getBundle"),
    @LinkRest(name = BitstreamRest.FORMAT, method = "getFormat"),
    @LinkRest(name = BitstreamRest.THUMBNAIL, method = "getThumbnail")
})
public class BitstreamRest extends DSpaceObjectRest {
    public static final String PLURAL_NAME = "bitstreams";
    public static final String NAME = "bitstream";
    public static final String CATEGORY = RestAddressableModel.CORE;

    public static final String BUNDLE = "bundle";
    public static final String FORMAT = "format";
    public static final String THUMBNAIL = "thumbnail";

    private String bundleName;

    private Long sizeBytes;
    private CheckSumRest checkSum;
    // sequenceId is READ_ONLY because it is assigned by the ItemService (as it must be unique within an Item)
    @JsonProperty(access = Access.READ_ONLY)
    private Integer sequenceId;

    public String getBundleName() {
        return bundleName;
    }

    public void setBundleName(String bundleName) {
        this.bundleName = bundleName;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public CheckSumRest getCheckSum() {
        return checkSum;
    }

    public void setCheckSum(CheckSumRest checkSum) {
        this.checkSum = checkSum;
    }

    public Integer getSequenceId() {
        return sequenceId;
    }

    public void setSequenceId(Integer sequenceId) {
        this.sequenceId = sequenceId;
    }

    @Override
    public String getCategory() {
        return CATEGORY;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getType() {
        return NAME;
    }

    @Override
    public String getTypePlural() {
        return PLURAL_NAME;
    }

    // UMD Customization

    /**
     * A String representing whether (and how long) this bitstream is embargoed.
     */
    public String embargoRestriction;

    /**
     * Returns one of the following Strings, based on whether the given
     * DSpace object has an embargo-based restriction:
     *
     * <ul>
     * <li> A date string (in "yyyy-MM-dd" format) - The lift date of the embargo
     * <li> "FOREVER" - the embargo never ends
     * <li> "NONE" - there is no embargo (or the embargo lift date has passed)
     * </ul>
     *
     * @return a String indicating whether (and how long) this bitstream is
     * embargoed
     */
    public String getEmbargoRestriction() {
        return embargoRestriction;
    }

    /**
     * Sets whether (and how long) this bitstream is embargoed
     *
     * @param embargoRestriction the String indicating the embargo restriction,
     * if any.
     */
    public void setEmbargoRestriction(String embargoRestriction) {
        this.embargoRestriction = embargoRestriction;
    }

    // End UMD Customization
}
