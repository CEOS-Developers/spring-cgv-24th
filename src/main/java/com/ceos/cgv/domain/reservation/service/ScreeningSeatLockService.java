package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreeningSeatLockService {
    private final ScreeningSeatRepository screeningSeatRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<ScreeningSeat> lockSeats(Long screeningId, Collection<SeatCoordinate> coordinates) {
        if (coordinates.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        List<SeatCoordinate> sorted = new ArrayList<>(coordinates);
        sorted.sort(Comparator.comparing(SeatCoordinate::row).thenComparingInt(SeatCoordinate::number));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).equals(sorted.get(i))) {
                throw new BusinessException(ErrorCode.DUPLICATE_SEAT_IN_REQUEST);
            }
        }

        List<ScreeningSeat> locked = new ArrayList<>(sorted.size());
        for (SeatCoordinate coordinate : sorted) {
            try {
                locked.add(screeningSeatRepository.lockCoordinateNowait(
                                screeningId, coordinate.row(), coordinate.number())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY)));
            } catch (PessimisticLockingFailureException | LockTimeoutException
                     | PessimisticLockException exception) {
                throw new BusinessException(ErrorCode.SEAT_BUSY);
            }
        }
        return locked;
    }
}
