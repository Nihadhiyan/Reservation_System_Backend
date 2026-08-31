package com.bookfair.backend.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import com.bookfair.backend.event.cache.LayoutUpdatedEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookfair.backend.event.layout.HallDimensionsChangedEvent;
import com.bookfair.backend.event.stall.StallDeactivatedEvent;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.stall.mapper.StallMapper;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.LayoutPosition;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.LayoutMarkerRepository;
import com.bookfair.backend.repository.StallRepository;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LayoutGenerationService {

    private final HallRepository hallRepository;
    private final StallRepository stallRepository;
    private final LayoutMarkerRepository layoutMarkerRepository;
    private final CommonMapper commonMapper;
    private final StallMapper stallMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public List<Stall> autoGenerateStallGrid(UUID hallId, int rows, int columns, int stallWidth, int stallLength,
            int aisleWidth, int startX, int startY) {
        requireNonNull(hallId, "hallId cannot be null");

        if (rows <= 0 || columns <= 0 || stallWidth <= 0 || stallLength <= 0 || aisleWidth < 0 || startX < 0 || startY < 0) {
            throw new BusinessException("Grid dimensions and coordinates must be positive values.", ErrorCode.VALIDATION_ERROR);
        }

        if ((long) rows * columns > 1000) {
            throw new BusinessException("Grid generation exceeds maximum safety limit of 1000 stalls per request.", ErrorCode.VALIDATION_ERROR);
        }

        // Lock the Hall row for the duration of this transaction so that a concurrent
        // Hall resize (HallService.updateHall) cannot interleave with grid generation
        // and produce stalls that fall outside the (possibly shrinking) Hall bounds.
        Hall hall = hallRepository.findByIdForUpdate(requireNonNull(hallId))
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found", ErrorCode.HALL_NOT_FOUND));

        List<Stall> existingStalls = stallRepository.findByHallIdAndActiveTrue(hallId);
        int currentCount = existingStalls.size();

        if (hall.getMaxStalls() != null && (currentCount + ((long) rows * columns)) > hall.getMaxStalls()) {
            throw new BusinessException(String.format(
                "Generating %d stalls exceeds Hall capacity limit of %d (Current active stalls: %d)",
                ((long) rows * columns), hall.getMaxStalls(), currentCount), ErrorCode.VALIDATION_ERROR);
        }

        if (hall.getLayout() != null && hall.getLayout().getWidth() != null && hall.getLayout().getHeight() != null) {
            int totalGridWidth = startX + (columns * stallWidth) + ((columns - 1) * aisleWidth);
            int totalGridHeight = startY + (rows * stallLength) + ((rows - 1) * aisleWidth);
            if (totalGridWidth > hall.getLayout().getWidth() || totalGridHeight > hall.getLayout().getHeight()) {
                throw new BusinessException("Generated grid physical boundaries exceed parent Hall layout dimensions.", ErrorCode.VALIDATION_ERROR);
            }
        } else {
            log.warn("Hall [{}] has no layout dimensions set — grid boundary validation skipped", hallId);
        }

        String hallName = hall.getName() != null ? hall.getName() : "STL";
        String prefix = hallName.replaceAll("[^A-Za-z0-9]", "");
        prefix = (prefix.length() >= 3 ? prefix.substring(0, 3) : prefix).toUpperCase();
        if (prefix.isEmpty()) prefix = "STL";

        // Fetch existing stall names/markers once, up front, instead of re-querying the DB
        // per generated cell (was O(rows*columns*existingStalls) queries and extended lock hold time).
        Set<String> existingNames = new HashSet<>();
        int maxExistingSuffix = 0;
        Pattern suffixPattern = Pattern.compile("-(\\d+)$");
        for (Stall existing : existingStalls) {
            existingNames.add(existing.getName());
            Matcher m = suffixPattern.matcher(existing.getName());
            if (m.find()) {
                try {
                    maxExistingSuffix = Math.max(maxExistingSuffix, Integer.parseInt(m.group(1)));
                } catch (NumberFormatException ignored) {
                    // non-numeric suffix, skip
                }
            }
        }
        List<LayoutMarker> existingMarkers = layoutMarkerRepository.findByHallIdAndActiveTrue(hallId);

        int stallCounter = maxExistingSuffix + 1;
        List<Stall> newStalls = new ArrayList<>();
        int currentY = startY;

        for (int r = 0; r < rows; r++) {
            int currentX = startX;
            for (int c = 0; c < columns; c++) {
                String name;
                int attempts = 0;
                do {
                    name = String.format("%s-%d", prefix, stallCounter++);
                    if (attempts++ > 10_000) {
                        throw new BusinessException("Unable to generate a unique stall name for Hall " + hallId, ErrorCode.VALIDATION_ERROR);
                    }
                } while (existingNames.contains(name));
                existingNames.add(name);

                Double sqFootage = (double) (stallWidth * stallLength);
                LayoutPosition layout = commonMapper.toLayoutPositionFromCoords(currentX, currentY, stallWidth, stallLength);

                validateSpatialConstraintsInMemory(hall, layout, existingStalls, existingMarkers, null);

                Stall stall = stallMapper.toGeneratedStall(hall, name, sqFootage, layout);
                newStalls.add(stall);

                currentX += stallWidth + aisleWidth;
            }
            currentY += stallLength + aisleWidth;
        }

        log.info("Auto-generated {} stalls for Hall {}", newStalls.size(), hallId);
        List<Stall> savedStalls = stallRepository.saveAll(newStalls);
        // Publish event to trigger AFTER_COMMIT cache eviction for both hall layout and admin dashboard metrics
        eventPublisher.publishEvent(new LayoutUpdatedEvent(hallId));
        return savedStalls;
    }

    @Transactional
    public Stall updateStallCoordinates(UUID stallId, LayoutPositionDto layoutPositionDto) {
        requireNonNull(layoutPositionDto, "layoutPositionDto cannot be null");
        Stall stall = stallRepository.findById(requireNonNull(stallId))
                .orElseThrow(() -> new ResourceNotFoundException("Stall not found", ErrorCode.STALL_NOT_FOUND));

        // Lock the parent Hall so this coordinate change cannot race with a concurrent
        // Hall resize or grid generation on the same Hall.
        // Acquire pessimistic lock on parent Hall to prevent concurrent Hall resize
        // racing with this coordinate update. Return value intentionally discarded.
        if (stall.getHall() != null) {
            hallRepository.findByIdForUpdate(stall.getHall().getId());
        }

        LayoutPosition newLayout = commonMapper.toLayoutPosition(layoutPositionDto);
        validateSpatialConstraints(stall.getHall(), newLayout, stallId);
        stall.setLayout(newLayout);

        log.info("Updated coordinates for stall {}", stallId);
        Stall savedStall = stallRepository.save(stall);
        // Publish event to trigger AFTER_COMMIT cache eviction
        eventPublisher.publishEvent(new LayoutUpdatedEvent(savedStall.getHall().getId()));
        return savedStall;
    }

    public void validateSpatialConstraints(Hall hall, LayoutPosition newLayout, UUID currentStallId) {
        List<Stall> existingStalls = hall != null ? stallRepository.findByHallIdAndActiveTrue(hall.getId()) : List.of();
        List<LayoutMarker> existingMarkers = hall != null ? layoutMarkerRepository.findByHallIdAndActiveTrue(hall.getId()) : List.of();
        validateSpatialConstraintsInMemory(hall, newLayout, existingStalls, existingMarkers, currentStallId);
    }

    /**
     * Same as {@link #validateSpatialConstraints}, but takes pre-fetched existing stalls/markers
     * so a caller validating many stalls in the same Hall (e.g. batch stall creation) can fetch
     * once per Hall instead of once per stall.
     */
    public void validateSpatialConstraints(Hall hall, LayoutPosition newLayout, UUID currentStallId,
            List<Stall> existingStalls, List<LayoutMarker> existingMarkers) {
        validateSpatialConstraintsInMemory(hall, newLayout, existingStalls, existingMarkers, currentStallId);
    }

    /**
     * Same validation as {@link #validateSpatialConstraints}, but takes pre-fetched
     * existing stalls/markers instead of re-querying the DB for every candidate cell
     * (used by the grid-generation loop, which would otherwise issue O(rows*columns)
     * redundant queries and hold the Hall lock far longer than necessary).
     */
    private void validateSpatialConstraintsInMemory(Hall hall, LayoutPosition newLayout,
            List<Stall> existingStalls, List<LayoutMarker> existingMarkers, UUID currentStallId) {
        if (newLayout == null || newLayout.getXCoord() == null || newLayout.getYCoord() == null
                || newLayout.getWidth() == null || newLayout.getHeight() == null) {
            throw new BusinessException("Layout position coordinates and dimensions must not be null", ErrorCode.VALIDATION_ERROR);
        }
        if (newLayout.getXCoord() < 0 || newLayout.getYCoord() < 0 || newLayout.getWidth() <= 0 || newLayout.getHeight() <= 0) {
            throw new BusinessException("Layout coordinates must be non-negative and dimensions must be positive", ErrorCode.VALIDATION_ERROR);
        }
        if (hall != null && hall.getLayout() != null && hall.getLayout().getWidth() != null && hall.getLayout().getHeight() != null) {
            if (newLayout.getXCoord() + newLayout.getWidth() > hall.getLayout().getWidth()
                    || newLayout.getYCoord() + newLayout.getHeight() > hall.getLayout().getHeight()) {
                throw new BusinessException("Stall layout exceeds parent Hall layout dimensions.", ErrorCode.VALIDATION_ERROR);
            }
        }
        if (hall != null) {
            for (Stall existing : existingStalls) {
                if (currentStallId != null && existing.getId().equals(currentStallId)) {
                    continue;
                }
                if (hasValidLayout(existing.getLayout())
                        && rectanglesOverlap(newLayout.getXCoord(), newLayout.getYCoord(), newLayout.getWidth(), newLayout.getHeight(),
                            existing.getLayout().getXCoord(), existing.getLayout().getYCoord(), existing.getLayout().getWidth(), existing.getLayout().getHeight())) {
                    throw new BusinessException("Stall spatial layout overlaps with existing stall: " + existing.getName(), ErrorCode.VALIDATION_ERROR);
                }
            }
            for (LayoutMarker marker : existingMarkers) {
                if (hasValidLayout(marker.getLayout())
                        && rectanglesOverlap(newLayout.getXCoord(), newLayout.getYCoord(), newLayout.getWidth(), newLayout.getHeight(),
                            marker.getLayout().getXCoord(), marker.getLayout().getYCoord(), marker.getLayout().getWidth(), marker.getLayout().getHeight())) {
                    throw new BusinessException("Stall spatial layout overlaps with existing layout marker: " + marker.getLabel(), ErrorCode.VALIDATION_ERROR);
                }
            }
        }
    }

    private boolean hasValidLayout(LayoutPosition layout) {
        return layout != null && layout.getXCoord() != null && layout.getYCoord() != null
                && layout.getWidth() != null && layout.getHeight() != null;
    }

    private boolean rectanglesOverlap(Integer x1, Integer y1, Integer w1, Integer h1, Integer x2, Integer y2, Integer w2, Integer h2) {
        if (x1 == null || y1 == null || w1 == null || h1 == null || x2 == null || y2 == null || w2 == null || h2 == null) {
            throw new BusinessException("Rectangle coordinates/dimensions must not be null", ErrorCode.VALIDATION_ERROR);
        }
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    // Cache physical stall layout lists by hall ID
    @Cacheable(value = "hallLayout", key = "#hallId")
    @Transactional(readOnly = true)
    public List<StallResponse> getHallLayout(UUID hallId) {
        if (!hallRepository.existsByIdAndActiveTrue(requireNonNull(hallId))) {
            throw new ResourceNotFoundException("Hall not found", ErrorCode.HALL_NOT_FOUND);
        }
        return stallRepository.findByHallIdAndActiveTrue(hallId).stream()
                .map(stallMapper::toStallResponse)
                .toList();
    }

    @Async
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onHallDimensionsChanged(HallDimensionsChangedEvent event) {
        log.info("Verifying stall bounding box compliance after Hall {} dimension update to {}x{}",
                event.hallId(), event.newWidth(), event.newHeight());
        List<Stall> stalls = stallRepository.findByHallIdAndActiveTrue(event.hallId());
        List<Stall> outOfBoundsStalls = new ArrayList<>();
        for (Stall stall : stalls) {
            if (stall.getLayout() != null && stall.getLayout().getXCoord() != null && stall.getLayout().getYCoord() != null
                    && stall.getLayout().getWidth() != null && stall.getLayout().getHeight() != null) {
                int stallRight = stall.getLayout().getXCoord() + stall.getLayout().getWidth();
                int stallBottom = stall.getLayout().getYCoord() + stall.getLayout().getHeight();
                if (stallRight > event.newWidth() || stallBottom > event.newHeight()) {
                    log.warn("FLAGGED STALL OUT OF BOUNDS: Stall {} ({}) exceeds new Hall dimensions (Stall bottom-right: {},{} vs Hall: {},{})",
                            stall.getId(), stall.getName(), stallRight, stallBottom, event.newWidth(), event.newHeight());
                    outOfBoundsStalls.add(stall);
                }
            }
        }
        if (!outOfBoundsStalls.isEmpty()) {
            log.warn("Total flagged stalls out of bounds for Hall {}: {}. Deactivating them so they cannot remain bookable outside the Hall's new footprint.",
                    event.hallId(), outOfBoundsStalls.size());
            outOfBoundsStalls.forEach(s -> s.setActive(false));
            stallRepository.saveAll(outOfBoundsStalls);
            // Reuse the existing deactivation cascade so any EventStall assignments
            // for this stall are blocked too, instead of silently remaining bookable.
            // Note: StallDeactivatedEvent is published inside an @Async method. Listeners
            // will run after this REQUIRES_NEW transaction commits, potentially on yet another thread.
            outOfBoundsStalls.forEach(s -> 
                eventPublisher.publishEvent(new StallDeactivatedEvent(s.getId())));
        }
    }
}
