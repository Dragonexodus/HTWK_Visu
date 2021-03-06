package org.htwkvisu.org.pois;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import org.htwkvisu.domain.ScoreValue;
import org.htwkvisu.gui.MapCanvas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.htwkvisu.org.pois.ScoreType.*;

public enum Category implements IScorable {
    EDUCATION(Color.RED, SCHOOL, COLLEGE, LIBRARY, MUSEUM, RESEARCH_INSTITUTION, THEATRE), HEALTH(Color.GREEN, PHARMACY, HOSPITAL, DENTIST, VETERINARY, DOCTORS, BLOOD_DONATION), INFRASTRUCTURE(Color.BLUE, TERMINAL, HELIPAD, AERODROME, BUS, TRAIN, TRAM);

    private static final int NEUTRAL_ELEMENT_DIV = 1;
    private List<ScoreType> types = new ArrayList<>();
    private final Color color;
    private int maxScoreValue = 1;

    Category(Color color, ScoreType... subcategories) {
        types.addAll(Arrays.asList(subcategories));
        types.stream().forEach(t -> t.setCategory(this));
        this.color = color;
    }

    public List<ScoreType> getTypes() {
        return types;
    }

    public Map<ScoreType, List<ScoreValue>> findAllInCategory() {
        return types.stream().collect(Collectors.toMap(Function.identity(), ScoreType::findAll));
    }

    public Map<ScoreType, List<BasicPOI>> generateDrawableInCategory() {
        return types.stream().collect(Collectors.toMap(Function.identity(), ScoreType::generateDrawable));
    }

    public List<ScoreValue> findAll() {
        return types.stream().flatMap(t -> t.findAll().stream()).collect(Collectors.toList());
    }

    public List<BasicPOI> generateDrawable() {
        return types.stream().flatMap(t -> t.generateDrawable().stream()).collect(Collectors.toList());
    }

    public List<ScoreValue> findAllEnabled() {
        return types.stream().filter(ScoreType::isEnabled).flatMap(t -> t.findAll().stream())
                .collect(Collectors.toList());
    }

    public List<BasicPOI> generateEnabledDrawable() {
        return types.stream().filter(ScoreType::isEnabled).flatMap(t -> t.generateDrawable().stream())
                .collect(Collectors.toList());
    }

    public void setEnabledForCategory(boolean enabled) {
        types.parallelStream().forEach(t -> t.setEnabled(enabled));
    }

    @Override
    public double calculateScoreValue(Point2D pt) {
        return types.stream().mapToDouble(t -> t.calculateScoreValue(pt)).sum();
    }

    public double calculateEnabledScoreValue(Point2D pt) {
        return types.stream().filter(ScoreType::isEnabled).mapToDouble(t -> t.calculateScoreValue(pt)).sum();
    }

    @Override
    public double calculateScoreValueForCustom(Point2D pt) {
        return types.stream().mapToDouble(t -> t.calculateScoreValueForCustom(pt)).sum();
    }

    public Color getColor() {
        return color;
    }

    public int getMaxScoreValue() {
        return maxScoreValue < NEUTRAL_ELEMENT_DIV ? NEUTRAL_ELEMENT_DIV : maxScoreValue;
    }

    public void setMaxScoreValue(int maxScoreValue) {
        this.maxScoreValue = maxScoreValue;
    }

    public void updateMaxScoreValue(MapCanvas canvas) {
        if (Arrays.stream(ScoreType.values()).anyMatch(ScoreType::isEnabled)) {
            this.maxScoreValue = canvas.calculateMaxScore(this);
        }
    }
}
