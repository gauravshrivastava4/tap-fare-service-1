package com.littlepay.tapfare.service;

import com.littlepay.tapfare.constant.TapType;
import com.littlepay.tapfare.constant.TripStatus;
import com.littlepay.tapfare.model.Tap;
import com.littlepay.tapfare.model.Trip;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripsCreationServiceTest {

    @Mock
    private FareCalculator fareCalculator;

    @InjectMocks
    private TripsCreationService tripsCreationService;

    @Test
    void testCreateCompletedAndCancelledTripsOnWithMatchingOffTap() {
        final Tap tapOn = new Tap(1L, LocalDateTime.of(2023, 1, 22, 13, 0), TapType.ON, "Stop1", "Company1", "Bus37", "4111111111111111");
        final Tap tapOff = new Tap(2L, LocalDateTime.of(2023, 1, 22, 13, 5), TapType.OFF, "Stop2", "Company1", "Bus37", "4111111111111111");

        when(fareCalculator.calculateFare("Stop1", "Stop2")).thenReturn(3.25);

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOff, tapOn));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(3.25, trip.getChargeAmount());
        assertEquals("Stop1", trip.getFromStopId());
        assertEquals("Stop2", trip.getToStopId());
    }

    @Test
    void testCreateCompletedAndCancelledTripsOffWithMatchingOnTap() {
        final Tap tapOn = new Tap(1L, LocalDateTime.of(2023, 1, 22, 13, 0), TapType.ON, "Stop1", "Company1", "Bus37", "4111111111111111");
        final Tap tapOff = new Tap(2L, LocalDateTime.of(2023, 1, 22, 13, 5), TapType.OFF, "Stop2", "Company1", "Bus37", "4111111111111111");

        when(fareCalculator.calculateFare("Stop1", "Stop2")).thenReturn(2.50);

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOn, tapOff));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.COMPLETED, trip.getStatus());
        assertEquals(2.50, trip.getChargeAmount());
        assertEquals("Stop1", trip.getFromStopId());
        assertEquals("Stop2", trip.getToStopId());
    }

    @Test
    void testCreateCompletedAndCancelledTripsOnWithoutOffTap() {
        final Tap tapOn = new Tap(1L, LocalDateTime.of(2023, 1, 22, 13, 0), TapType.ON, "Stop1", "Company1", "Bus37", "4111111111111111");

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOn));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.INCOMPLETE, trip.getStatus());
    }

    @Test
    void testCreateCompletedAndCancelledTripsForOrphanOnTap() {
        final Tap tapOn = new Tap(1L, LocalDateTime.of(2023, 1, 22, 13, 0), TapType.ON, "Stop1", "Company1", "Bus37", "4111111111111111");

        when(fareCalculator.calculateMaxFare("Stop1")).thenReturn(5.50);

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOn));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.INCOMPLETE, trip.getStatus());
        assertEquals(5.50, trip.getChargeAmount());
    }

    @Test
    void testCreateCompletedAndCancelledTripsForOrphanOffTap() {
        final Tap tapOff = new Tap(2L, LocalDateTime.of(2023, 1, 22, 13, 5), TapType.OFF, "Stop2", "Company1", "Bus37", "4111111111111111");

        when(fareCalculator.calculateMaxFare("Stop2")).thenReturn(7.00);

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOff));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.INCOMPLETE, trip.getStatus());
        assertEquals(7.00, trip.getChargeAmount());
    }

    @Test
    void testCancelledTripWhenTapOnAndOffAtSameStop() {
        final Tap tapOn = new Tap(1L, LocalDateTime.of(2023, 1, 22, 13, 0), TapType.ON, "Stop1", "Company1", "Bus37", "4111111111111111");
        final Tap tapOff = new Tap(2L, LocalDateTime.of(2023, 1, 22, 13, 5), TapType.OFF, "Stop1", "Company1", "Bus37", "4111111111111111");

        final List<Trip> trips = tripsCreationService.createTrips(List.of(tapOn, tapOff));

        assertEquals(1, trips.size());
        final Trip trip = trips.get(0);
        assertEquals(TripStatus.CANCELLED, trip.getStatus());
        assertEquals(0.0, trip.getChargeAmount());
    }
}


