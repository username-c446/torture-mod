package com.torturemod.api.resistance;

import java.util.Map;

/** @param effectiveness действенность каждого типа пытки от 0 до 1: падает от привыкания */
public record ResistanceView(double value, double max, boolean broken, Map<String, Double> effectiveness) {
}
