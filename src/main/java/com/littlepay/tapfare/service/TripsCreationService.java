package com.littlepay.tapfare.service;

import com.littlepay.tapfare.constant.TapType;
import com.littlepay.tapfare.constant.TripStatus;
import com.littlepay.tapfare.model.Tap;
import com.littlepay.tapfare.model.Trip;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TripsCreationService {

    private final FareCalculator fareCalculator;

    public TripsCreationService(final FareCalculator fareCalculator) {
        this.fareCalculator = fareCalculator;
    }

    public List<Trip> createTrips(final List<Tap> taps) {
        final List<Trip> trips = new ArrayList<>();
        Tap previousTap = null;
        TripStatus previousTripStatus = null;

        for (int i = 0; i < taps.size(); i++) {
            final Tap currentTap = taps.get(i);

            // Process ON taps
            if (currentTap.getTapType() == TapType.ON) {
                final Trip trip = processTapON(taps, currentTap, i, trips);
                previousTap = currentTap;
                previousTripStatus = trip.getStatus();
            }

            // Process orphaned OFF taps
            else if (currentTap.getTapType() == TapType.OFF) {
                processOrphanedTapOff(taps, i, previousTap, previousTripStatus, currentTap, trips);
            }
        }
        return trips;
    }

    private Trip processTapON(final List<Tap> taps, final Tap currentTap, final int i, final List<Trip> trips) {
        final Tap tapOff = findMatchingTapOff(taps, currentTap.getPan(), currentTap.getLocalDateTime(), i);
        final Trip trip = createTrip(currentTap, tapOff);
        trips.add(trip);
        return trip;
    }

    private void processOrphanedTapOff(final List<Tap> taps, final int i, final Tap previousTap, final TripStatus previousTripStatus, final Tap currentTap, final List<Trip> trips) {
        final var isLastTap = isLastTap(taps, i);
        if (isOrphanedOffTap(previousTap, previousTripStatus, currentTap, isLastTap)) {
            trips.add(handleOrphanTapOff(currentTap));
        }
    }

    private static boolean isLastTap(final List<Tap> taps, final int i) {
        return i == taps.size() - 1;
    }

    private boolean isOrphanedOffTap(final Tap previousTap, final TripStatus previousTripStatus, final Tap currentTap, final boolean isLastTap) {
        return previousTap == null ||
                (!previousTripStatus.equals(TripStatus.COMPLETED) && !previousTap.getPan().equals(currentTap.getPan()) && isLastTap);
    }

    private Tap findMatchingTapOff(final List<Tap> taps, final String pan, final LocalDateTime localDateTime, final int index) {
        return taps.stream()
                .skip(index + 1)
                .filter(tap -> tap.getPan().equals(pan) && tap.getTapType() == TapType.OFF &&
                        tap.getLocalDateTime().toLocalDate().isEqual(localDateTime.toLocalDate()))//Assuming that tap on and tap off would belong to the same date
                .findFirst()
                .orElse(null);
    }

    private Trip createTrip(final Tap tapOn, final Tap tapOff) {
        if (tapOff == null) {
            return createIncompleteTrip(tapOn);
        }
        if (tapOn.getStopId().equals(tapOff.getStopId())) {
            return createCancelledTrip(tapOn, tapOff);
        }
        return createCompletedTrip(tapOn, tapOff);
    }

    private Trip handleOrphanTapOff(final Tap tapOff) {
        final double maxFare = fareCalculator.calculateMaxFare(tapOff.getStopId());
        return createTripWithoutOn(tapOff, maxFare);
    }

    private Trip createIncompleteTrip(final Tap tapOn) {
        final double maxFare = fareCalculator.calculateMaxFare(tapOn.getStopId());
        return createTripWithoutOff(tapOn, maxFare);
    }

    private Trip createCancelledTrip(final Tap tapOn, final Tap tapOff) {
        return createTripWithDetails(tapOn, tapOff, 0, 0.0, TripStatus.CANCELLED);
    }

    private Trip createCompletedTrip(final Tap tapOn, final Tap tapOff) {
        final long durationSecs = Duration.between(tapOn.getLocalDateTime(), tapOff.getLocalDateTime()).getSeconds();
        final double fare = fareCalculator.calculateFare(tapOn.getStopId(), tapOff.getStopId());
        return createTripWithDetails(tapOn, tapOff, durationSecs, fare, TripStatus.COMPLETED);
    }

    private Trip createTripWithDetails(final Tap tapOn, final Tap tapOff, final long durationSecs, final double fare, final TripStatus status) {
        return new Trip(
                tapOn.getLocalDateTime(),
                tapOff.getLocalDateTime(),
                durationSecs,
                tapOn.getStopId(),
                tapOff.getStopId(),
                fare,
                tapOn.getCompanyId(),
                tapOn.getBusId(),
                tapOn.getPan(),
                status
        );
    }

    private Trip createTripWithoutOff(final Tap tapOn, final double fare) {
        return new Trip(
                tapOn.getLocalDateTime(),
                null,
                0,
                tapOn.getStopId(),
                null,
                fare,
                tapOn.getCompanyId(),
                tapOn.getBusId(),
                tapOn.getPan(),
                TripStatus.INCOMPLETE
        );
    }

    private Trip createTripWithoutOn(final Tap tapOff, final double fare) {
        return new Trip(
                null,
                tapOff.getLocalDateTime(),
                0,
                null,
                tapOff.getStopId(),
                fare,
                tapOff.getCompanyId(),
                tapOff.getBusId(),
                tapOff.getPan(),
                TripStatus.INCOMPLETE
        );
    }
}
