package fr.alb.yard.model;

/**
 * Embedded POJO carrying Out-of-Gauge dimension overages.
 * Populated only when {@code Item#oogFlag} is {@code true}.
 */
public class OogDimensions {

    /** Overage above standard container height, in centimetres. */
    private double heightOverageCm;

    /** Overage beyond standard container width, in centimetres. */
    private double widthOverageCm;

    /** Overage beyond standard container length, in centimetres. */
    private double lengthOverageCm;

    /** Absolute overall height of the cargo/container, in centimetres. */
    private double overallHeightCm;

    /** Absolute overall width of the cargo/container, in centimetres. */
    private double overallWidthCm;

    /** Absolute overall length of the cargo/container, in centimetres. */
    private double overallLengthCm;

    /** Free-text notes regarding the OOG condition. */
    private String notes;

    public OogDimensions() {
    }

    public double getHeightOverageCm() {
        return heightOverageCm;
    }

    public void setHeightOverageCm(double heightOverageCm) {
        this.heightOverageCm = heightOverageCm;
    }

    public double getWidthOverageCm() {
        return widthOverageCm;
    }

    public void setWidthOverageCm(double widthOverageCm) {
        this.widthOverageCm = widthOverageCm;
    }

    public double getLengthOverageCm() {
        return lengthOverageCm;
    }

    public void setLengthOverageCm(double lengthOverageCm) {
        this.lengthOverageCm = lengthOverageCm;
    }

    public double getOverallHeightCm() {
        return overallHeightCm;
    }

    public void setOverallHeightCm(double overallHeightCm) {
        this.overallHeightCm = overallHeightCm;
    }

    public double getOverallWidthCm() {
        return overallWidthCm;
    }

    public void setOverallWidthCm(double overallWidthCm) {
        this.overallWidthCm = overallWidthCm;
    }

    public double getOverallLengthCm() {
        return overallLengthCm;
    }

    public void setOverallLengthCm(double overallLengthCm) {
        this.overallLengthCm = overallLengthCm;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
