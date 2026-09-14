package com.ceos24.cgv.domain.cinema.initializer;

import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import com.ceos24.cgv.domain.cinema.entity.AuditoriumTypeCategory;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditoriumTypeInitializer implements ApplicationRunner {

    private final AuditoriumTypeRepository auditoriumTypeRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {

        saveIfAbsent("일반관", AuditoriumTypeCategory.NORMAL, 10, 10);
        saveIfAbsent("특별관", AuditoriumTypeCategory.SPECIAL, 15, 20);
    }

    private void saveIfAbsent(
            String name,
            AuditoriumTypeCategory category,
            int rowCount,
            int columnCount
    ) {
        if (auditoriumTypeRepository.existsByName(name)) {
            return;
        }

        AuditoriumType type = AuditoriumType.create(
                name,
                category,
                rowCount,
                columnCount
        );

        auditoriumTypeRepository.save(type);
    }
}
