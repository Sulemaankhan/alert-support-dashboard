package com.supportalert.bootstrap;

import com.supportalert.issue.IssueService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeedRunner implements ApplicationRunner {

    private final IssueService issueService;

    public DataSeedRunner(IssueService issueService) {
        this.issueService = issueService;
    }

    @Override
    public void run(ApplicationArguments args) {
        issueService.seedFromUserManagerSample();
    }
}
