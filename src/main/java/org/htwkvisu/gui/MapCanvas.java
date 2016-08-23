package org.htwkvisu.gui;

import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.htwkvisu.org.IMapDrawable;
import org.htwkvisu.org.pois.BasicPOI;
import org.htwkvisu.org.pois.NormalizedColorCalculator;
import org.htwkvisu.org.pois.ScoringCalculator;
import org.htwkvisu.utils.MathUtils;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Canvas for map.
 */
public class MapCanvas extends BasicCanvas {

    private Point2D mapCenter = new Point2D(50.832222, 12.92416666); //Chemnitz
    private GraphicsContext gc = getGraphicsContext2D();
    private double widthDistance = 0;
    private double heightDistance = 0;
    private int displayedElems = 0;
    private CheckBox colorModeCheckBox;
    private Grid grid = new Grid(this);

    public static final Point2D CITY_LEIPZIG = new Point2D(51.340333, 12.37475);

    /**
     * Construct and init canvas
     */
    public MapCanvas(ScoringConfig config) {
        super(config);

        // add test cities
        addDrawableElement(new City(CITY_LEIPZIG, "Leipzig", 0));
        addDrawableElement(new City(new Point2D(51.049259, 13.73836112), "Dresden", 0));
        addDrawableElement(new City(new Point2D(50.832222, 12.92416666), "Chemnitz", 0));
        addDrawableElement(new City(new Point2D(50.718888, 12.492222), "Zwickau", 0));
    }

    @Override
    protected void drawInfo() {
        gc.setFill(Color.GRAY);
        gc.fillText("Center: " + MathUtils.roundToDecimalsAsString(mapCenter.getX(), 5) + " " +
                MathUtils.roundToDecimalsAsString(mapCenter.getY(), 5), 10, 20);
        gc.fillText("Distance: " + MathUtils.roundToDecimalsAsString(widthDistance, 3) + " km x " + MathUtils.roundToDecimalsAsString(heightDistance, 3) + " km", 10, 40);
        gc.fillText("Elements displayed: " + displayedElems, 10, 60);
        gc.fillText("Scale: " + MathUtils.roundToDecimalsAsString(scale, 2), 10, 80);
        gc.fillText("Bounds: " + getCoordsBoundsAsString(), 10, 100);
    }

    @Override
    public void drawScoringValues() {

        // get sample points for canvas
        List<Point2D> gridPoints = calculateGrid();
        Color[] cols = new Color[gridPoints.size()];

        // save previous colors
        final Paint curFillPaint = gc.getFill();
        final Paint curStrokePaint = gc.getStroke();

        boolean useNorm = colorModeCheckBox != null ? colorModeCheckBox.isSelected() : false;
        NormalizedColorCalculator norm = new NormalizedColorCalculator(this, useNorm);

        final int pixelDensity = config.getSamplingPixelDensity();
        final int xSize = grid.getxSize();
        final int ySize = grid.getySize();

        // calculate values
        IntStream.range(0, ySize).parallel().forEach(y -> {
            IntStream.range(0, xSize).forEach(x -> {
                final int index = y * xSize + x;
                Point2D pt = gridPoints.get(index);
                cols[index] = norm.calculateColor(pt);
            });
        });

        // draw linear interpolated values
        PixelWriter pxWriter = gc.getPixelWriter();
        for (int y = 2; y < ySize - 1; y++) {
            for (int x = 2; x < xSize - 1; x++) {
                Point2D pt = gridPoints.get(y * xSize + x);
                Point2D pixelPos = transferCoordinateToPixel(pt);

                for (int xStep = 0; xStep < pixelDensity; xStep++) {
                    for (int yStep = 0; yStep < pixelDensity; yStep++) {
                        float xNorm = (float) xStep / pixelDensity;
                        float yNorm = (float) yStep / pixelDensity;

                        Color lerpedCol;

                        switch (config.getInterpolationMode()) {
                            case BILINEAR:
                                lerpedCol = interpolateBiLinear(cols, xSize, y, x, xNorm, yNorm);
                                break;
                            case BICUBIC:
                                lerpedCol = interpolateCubic(cols, xSize, y, x, 1 - xNorm, yNorm);
                                break;
                            default:
                                lerpedCol = Color.BLACK;
                        }

                        pxWriter.setColor((int) pixelPos.getX() + xStep, (int) pixelPos.getY() + yStep, lerpedCol);
                    }
                }
            }
        }

        //Restore previous colors
        gc.setFill(curFillPaint);
        gc.setStroke(curStrokePaint);
    }

    private Color interpolateBiLinear(Color[] cols, int xSize, int y, int x, float xNorm, float yNorm) {
        Color upperCol = cols[y * xSize + x].interpolate(cols[(y - 1) * xSize + x], yNorm);
        Color lowerCol = cols[y * xSize + x - 1].interpolate(cols[(y - 1) * xSize + x - 1], yNorm);

        return lowerCol.interpolate(upperCol, xNorm);
    }

    private Color interpolateCubic(Color[] cols, int xSize, int y, int x, float xNorm, float yNorm) {

        return Color.color(
                MathUtils.getBicubicValue(new double[][]{
                        {cols[(y + 1) * xSize + x + 1].getRed(), cols[y * xSize + x + 1].getRed(), cols[(y - 1) * xSize + x + 1].getRed(), cols[(y - 2) * xSize + x + 1].getRed()},
                        {cols[(y + 1) * xSize + x].getRed(), cols[y * xSize + x].getRed(), cols[(y - 1) * xSize + x].getRed(), cols[(y - 2) * xSize + x].getRed()},
                        {cols[(y + 1) * xSize + x - 1].getRed(), cols[y * xSize + x - 1].getRed(), cols[(y - 1) * xSize + x - 1].getRed(), cols[(y - 2) * xSize + x - 1].getRed()},
                        {cols[(y + 1) * xSize + x - 2].getRed(), cols[y * xSize + x - 2].getRed(), cols[(y - 1) * xSize + x - 2].getRed(), cols[(y - 2) * xSize + x - 2].getRed()}
                }, xNorm, yNorm),
                MathUtils.getBicubicValue(new double[][]{
                        {cols[(y + 1) * xSize + x + 1].getGreen(), cols[y * xSize + x + 1].getGreen(), cols[(y - 1) * xSize + x + 1].getGreen(), cols[(y - 2) * xSize + x + 1].getGreen()},
                        {cols[(y + 1) * xSize + x].getGreen(), cols[y * xSize + x].getGreen(), cols[(y - 1) * xSize + x].getGreen(), cols[(y - 2) * xSize + x].getGreen()},
                        {cols[(y + 1) * xSize + x - 1].getGreen(), cols[y * xSize + x - 1].getGreen(), cols[(y - 1) * xSize + x - 1].getGreen(), cols[(y - 2) * xSize + x - 1].getGreen()},
                        {cols[(y + 1) * xSize + x - 2].getGreen(), cols[y * xSize + x - 2].getGreen(), cols[(y - 1) * xSize + x - 2].getGreen(), cols[(y - 2) * xSize + x - 2].getGreen()}
                }, xNorm, yNorm),
                MathUtils.getBicubicValue(new double[][]{
                        {cols[(y + 1) * xSize + x + 1].getBlue(), cols[y * xSize + x + 1].getBlue(), cols[(y - 1) * xSize + x + 1].getBlue(), cols[(y - 2) * xSize + x + 1].getBlue()},
                        {cols[(y + 1) * xSize + x].getBlue(), cols[y * xSize + x].getBlue(), cols[(y - 1) * xSize + x].getBlue(), cols[(y - 2) * xSize + x].getBlue()},
                        {cols[(y + 1) * xSize + x - 1].getBlue(), cols[y * xSize + x - 1].getBlue(), cols[(y - 1) * xSize + x - 1].getBlue(), cols[(y - 2) * xSize + x - 1].getBlue()},
                        {cols[(y + 1) * xSize + x - 2].getBlue(), cols[y * xSize + x - 2].getBlue(), cols[(y - 1) * xSize + x - 2].getBlue(), cols[(y - 2) * xSize + x - 2].getBlue()}
                }, xNorm, yNorm));
    }

    public static double interpolateCubicFloat(double l, double lB, double r, double rA, double x) {
        double val = l + 0.5 * x * (r - lB + x * (2.0 * lB - 5.0 * l + 4.0 * r - rA + x * (3.0 * (l - r) + rA - lB)));
        if (val > 0 && val < 1) {
            return val;
        } else if (val <= 0) {
            return 0;
        } else {
            return 1;
        }

    }

    /*private float interpolateCubicFloat(float l, float lm, float r, float rm, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        return (2 * t3 - 3 * t2) * l + (t3 - 2 * t2 + t) * lm + (-2 * t3 + 3 * t2) * r + (t3 - t2) * rm;
    }*/

    @Override
    public void drawGrid() {
        double northPos;
        double eastPos;
        gc.setStroke(Color.BLACK);

        // Latitude
        double x = coordsBounds.getMinX();
        while (x < coordsBounds.getMaxX()) {
            double fullVal = Math.ceil(x);
            northPos = transferCoordinateToPixel(new Point2D(fullVal, Math.ceil(x))).getY();
            gc.strokeLine(0, northPos, tmpWidth, northPos);
            gc.fillText(MathUtils.roundToDecimalsAsString(fullVal, 0), 10, northPos - 10);
            x++;
        }

        // Longitude
        double y = coordsBounds.getMinY();
        while (y < coordsBounds.getMaxY()) {
            double fullVal = Math.ceil(y);
            eastPos = transferCoordinateToPixel(new Point2D(Math.ceil(y), fullVal)).getX();
            gc.strokeLine(eastPos, 0, eastPos, tmpHeight);
            gc.fillText(MathUtils.roundToDecimalsAsString(fullVal, 0), eastPos + 10, tmpHeight - 10);
            y++;
        }
    }

    @Override
    public void addDrawableElement(IMapDrawable elem) {
        if (elem == null) {
            throw new IllegalArgumentException("No valid element!");
        }

        drawables.add(elem);
    }


    private void drawPOIS() {
        final Paint curFillPaint = gc.getFill();
        final Paint curStrokePaint = gc.getStroke();

        for (BasicPOI poi : ScoringCalculator.generateEnabled()) {
            poi.draw(this.gc, this);
        }

        gc.setFill(curFillPaint);
        gc.setStroke(curStrokePaint);
    }


    @Override
    public void drawElements() {
        List<IMapDrawable> toDraw = drawables.parallelStream()
                .filter(p -> !isInDragMode() || p.showDuringGrab())
                .filter(p -> p.getMinDrawScale() < scale)
                .filter(p -> coordsBounds.contains(p.getCoordinates()))
                .collect(Collectors.toList());

        displayedElems = toDraw.size();
        for (IMapDrawable elem : toDraw) {
            elem.draw(this.gc, this);
        }
    }

    @Override
    public void centerView(Point2D center) {
        if (center != null) {
            mapCenter = center;
        } else {
            mapCenter = CITY_LEIPZIG;
        }
        redraw();
    }

    @Override
    public Point2D getCenter() {
        return mapCenter;
    }

    @Override
    public void redraw() {
        long tStart = System.currentTimeMillis();

        tmpWidth = getWidth();
        tmpHeight = getHeight();
        double coveredWidth = tmpWidth / scale;
        double coveredHeight = tmpHeight / scale;
        coordsBounds = new BoundingBox(mapCenter.getX() - coveredHeight / 2, mapCenter.getY() - coveredWidth / 2,
                coveredHeight, coveredWidth);
        heightDistance = MathUtils.convertUnitsToKilometres(coordsBounds.getWidth());
        widthDistance = MathUtils.convertUnitsToKilometres(coordsBounds.getHeight());

        // clear view
        gc.clearRect(0, 0, tmpWidth, tmpHeight);

        // draw map content
        if (!isInDragMode()) {
            drawScoringValues();
        }
        drawInfo();
        drawGrid();
        drawPOIS();
        drawElements();

        Logger.getGlobal().info("Redraw took " + (System.currentTimeMillis() - tStart) + " ms");
    }

    @Override
    public Point2D transferCoordinateToPixel(Point2D p) {
        return new Point2D((p.getY() - mapCenter.getY()) * scale + tmpWidth / 2,
                ((mapCenter.getX() - p.getX()) * scale + tmpHeight / 2));
    }

    @Override
    public Point2D transferPixelToCoordinate(double x, double y) {
        return new Point2D(coordsBounds.getMaxX() - (y / tmpHeight) * coordsBounds.getWidth(),
                coordsBounds.getMinY() + (x / tmpWidth) * coordsBounds.getHeight());
    }

    public int calculateMaxScore() {
        List<Point2D> gridPoints = calculateGrid();
        double score = 0.0;
        double tmp = 0.0;
        for (Point2D point : gridPoints) {
            tmp = ScoringCalculator.calculateEnabledScoreValue(point);
            if (score < tmp) {
                score = tmp;
            }
        }
        return (int) score;
    }

    public List<Point2D> calculateGrid() {
        return grid.calcGridPoints(config.getSamplingPixelDensity());
    }

    public void setColorModeCheckBox(CheckBox colorModeCheckBox) {
        this.colorModeCheckBox = colorModeCheckBox;
    }

    public CheckBox getColorModeCheckBox() {
        return colorModeCheckBox;
    }
}
