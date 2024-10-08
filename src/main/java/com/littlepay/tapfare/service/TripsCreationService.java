package com.littlepay.tapfare.service;

import com.littlepay.tapfare.constant.TapType;
import com.littlepay.tapfare.constant.TripStatus;
import com.littlepay.tapfare.model.Tap;
import com.littlepay.tapfare.model.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for creating trips from a list of taps.
 * This service processes a list of tap events to create completed, incomplete,
 * and cancelled trips. It manages the transitions between tap-on and tap-off
 * events, ensuring that every tap event is properly accounted for in the generated
 * trips.
 * This service uses {@link FareCalculator} to calculate the fare for each trip.
 */
@Service
public class TripsCreationService {

    private static final Logger logger = LoggerFactory.getLogger(TripsCreationService.class);

    private final FareCalculator fareCalculator;
    private static Map<String, List<Tap>> tapOnMap = new HashMap<>();
    private static Map<String, List<Tap>> tapOffMap = new HashMap<>();
    private static List<Trip> trips = new ArrayList<>();

    public TripsCreationService(final FareCalculator fareCalculator) {
        this.fareCalculator = fareCalculator;
    }


    /**
     * Creates a list of trips from the given list of taps.
     * It first creates Completed And Cancelled Trips
     * Then Creates Incomplete trips from Orphans
     *
     * @param taps the list of taps to process. Each tap represents Tap On or Tap Off.
     * @return a list of trips created from the taps.
     */
    public List<Trip> createTrips(final List<Tap> taps) {
        resetTrips();
        taps.forEach(this::createCompletedAndCancelledTrips);
        createTripsForOrphanTaps();
        return trips;
    }

    private void resetTrips() {
        tapOnMap = new HashMap<>();
        tapOffMap = new HashMap<>();
        trips = new ArrayList<>();
    }

    private void createCompletedAndCancelledTrips(final Tap tap) {
        logger.info("Processing tap: {}", tap);

        if (tap.getTapType() == TapType.ON) {
            handleTapOn(tap);
        } else if (tap.getTapType() == TapType.OFF) {
            handleTapOff(tap);
        } else {
            logger.warn("Unknown tap type: {}", tap.getTapType());
        }
    }


    /**
     * Processes orphan "Tap On" and "Tap Off" events to create incomplete trips.
     * This method is invoked after attempting to create completed and cancelled trips.
     * It processes the remaining unmatched "Tap On" and "Tap Off" events, generating
     * incomplete trips for each orphan tap.
     */
    private void createTripsForOrphanTaps() {
        processOrphanOnTaps();
        processOrphanOffTaps();
    }


    /**
     * Handles the "Tap On" event by attempting to match it with an existing "Tap Off" event to create a completed trip.
     * If a matching "Tap Off" event is found, a trip is created and logged; otherwise, the "Tap On" event is stored
     * for future processing.
     *
     * @param tapOn the tap on event to be handled. This event will be used to find a corresponding "Tap Off" event
     *              to complete the trip.
     */
    private void handleTapOn(final Tap tapOn) {
        final List<Tap> matchingOffTaps = tapOffMap.getOrDefault(tapOn.getPan(), new ArrayList<>());
        final Tap matchingTapOff = findMatchingTapOff(matchingOffTaps, tapOn.getPan(), tapOn.getLocalDateTime());

        if (matchingTapOff != null) {
            final Trip trip = createTrip(tapOn, matchingTapOff);
            trips.add(trip);
            matchingOffTaps.remove(matchingTapOff);
            logger.info("Completed trip created for PAN: {} from {} to {}", tapOn.getPan(), tapOn.getStopId(), matchingTapOff.getStopId());
        } else {
            tapOnMap.computeIfAbsent(tapOn.getPan(), k -> new ArrayList<>()).add(tapOn);
            logger.info("No matching OFF tap found for ON tap at stop {}, storing for later.", tapOn.getStopId());
        }
    }


    /**
     * Handles a "Tap Off" event by attempting to find a matching "Tap On" event.
     * If a matching "Tap On" event is found, a trip is created and logged.
     * Otherwise, the "Tap Off" event is stored for future processing.
     *
     * @param tapOff the tap off event to be handled. This event will be used
     *               to find a corresponding "Tap On" event to complete the trip.
     */
    private void handleTapOff(final Tap tapOff) {
        final List<Tap> matchingOnTaps = tapOnMap.getOrDefault(tapOff.getPan(), new ArrayList<>());
        final Tap matchingTapOn = findMatchingTapOn(matchingOnTaps, tapOff.getPan(), tapOff.getLocalDateTime());

        if (matchingTapOn != null) {
            final Trip trip = createTrip(matchingTapOn, tapOff);
            trips.add(trip);
            matchingOnTaps.remove(matchingTapOn);
            logger.info("Completed trip created for PAN: {} from {} to {}", tapOff.getPan(), matchingTapOn.getStopId(), tapOff.getStopId());
        } else {
            tapOffMap.computeIfAbsent(tapOff.getPan(), k -> new ArrayList<>()).add(tapOff);
            logger.info("No matching ON tap found for OFF tap at stop {}, storing for later.", tapOff.getStopId());
        }
    }

    private Tap findMatchingTapOff(final List<Tap> taps, final String pan, final LocalDateTime localDateTime) {
        return taps.stream()
                .filter(tap -> tap.getPan().equals(pan) && tap.getLocalDateTime().isAfter(localDateTime) &&
                        tap.getLocalDateTime().toLocalDate().isEqual(localDateTime.toLocalDate()))
                .findFirst()
                .orElse(null);
    }

    private Tap findMatchingTapOn(final List<Tap> taps, final String pan, final LocalDateTime localDateTime) {
        return taps.stream()
                .filter(tap -> tap.getPan().equals(pan) && tap.getLocalDateTime().isBefore(localDateTime) &&
                        tap.getLocalDateTime().toLocalDate().isEqual(localDateTime.toLocalDate()))
                .findFirst()
                .orElse(null);
    }

    private void processOrphanOnTaps() {
        tapOnMap.forEach((pan, taps) -> {
            for (final Tap tapOn : taps) {
                trips.add(createIncompleteTrip(tapOn));
                logger.info("Incomplete trip created for orphan ON tap at stop {}", tapOn.getStopId());
            }
        });
        tapOnMap.clear();
    }

    private void processOrphanOffTaps() {
        tapOffMap.forEach((pan, taps) -> {
            for (final Tap tapOff : taps) {
                trips.add(handleOrphanTapOff(tapOff));
                logger.info("Incomplete trip created for orphan OFF tap at stop {}", tapOff.getStopId());
            }
        });
        tapOffMap.clear();
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
                tapOff != null ? tapOff.getLocalDateTime() : null,
                durationSecs,
                tapOn.getStopId(),
                tapOff != null ? tapOff.getStopId() : null,
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
