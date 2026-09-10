package com.oodd.library.service;

import com.oodd.library.model.SystemSettings;
import com.oodd.library.repository.SystemSettingsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {

    @Autowired
    private SystemSettingsRepository systemSettingsRepository;

    @Transactional
    public SystemSettings getSettings() {
        return systemSettingsRepository.findAll()
                .stream()
                .findFirst()
                .orElseGet(() -> systemSettingsRepository.save(new SystemSettings()));
    }

    @Transactional
    public SystemSettings updateSettings(int maxLoanDays, double fineRate, int maxBorrowLimit) {
        SystemSettings settings = getSettings();
        settings.setMaxLoanDays(maxLoanDays);
        settings.setFineRate(fineRate);
        settings.setMaxBorrowLimit(maxBorrowLimit);
        return systemSettingsRepository.save(settings);
    }
}
