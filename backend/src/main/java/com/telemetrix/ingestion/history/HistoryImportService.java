package com.telemetrix.ingestion.history;

import com.telemetrix.common.ConflictException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

// Runs the Jolpica history import in the background: download the dump, then run the Batch job.
@Service
public class HistoryImportService {

	private static final Logger log = LoggerFactory.getLogger(HistoryImportService.class);

	private final JobOperator jobOperator;

	private final Job job;

	private final JobRepository jobRepository;

	private final JolpicaDumpDownloader downloader;

	private final Clock clock;

	private final Path workDir;

	private final TaskExecutor background = new SimpleAsyncTaskExecutor("history-import-");

	HistoryImportService(JobOperator jobOperator, Job jolpicaHistoryImport, JobRepository jobRepository,
			JolpicaDumpDownloader downloader, Clock clock,
			@Value("${telemetrix.import.work-dir:${java.io.tmpdir}/telemetrix-import}") Path workDir) {
		this.jobOperator = jobOperator;
		this.job = jolpicaHistoryImport;
		this.jobRepository = jobRepository;
		this.downloader = downloader;
		this.clock = clock;
		this.workDir = workDir;
	}

	/** The job's parameters: `run` makes each run a new job instance; the same values restart one. */
	public static JobParameters parameters(Path dumpDir, long run) {
		return new JobParametersBuilder()
				.addString("dumpDir", dumpDir.toAbsolutePath().toString())
				.addLong("run", run)
				.toJobParameters();
	}

	public HistoryImportStatus start() {
		if (!jobRepository.findRunningJobExecutions(HistoryImportJobConfig.JOB_NAME).isEmpty()) {
			throw new ConflictException("A history import is already running");
		}
		long run = clock.millis();
		background.execute(() -> {
			try {
				Path dumpDir = downloader.download(workDir.resolve(String.valueOf(run)));
				JobExecution execution = jobOperator.start(job, parameters(dumpDir, run));
				log.info("History import {} finished: {}", execution.getId(), execution.getStatus());
			}
			catch (Exception e) {
				log.error("History import failed", e);
			}
		});
		return new HistoryImportStatus(null, "STARTING", null, null, null);
	}

	public Optional<HistoryImportStatus> lastRun() {
		var instance = jobRepository.getLastJobInstance(HistoryImportJobConfig.JOB_NAME);
		if (instance == null) {
			return Optional.empty();
		}
		JobExecution execution = jobRepository.getLastJobExecution(instance);
		if (execution == null) {
			return Optional.empty();
		}
		return Optional.of(new HistoryImportStatus(execution.getId(), execution.getStatus().name(),
				instant(execution.getStartTime()), instant(execution.getEndTime()),
				execution.getExitStatus().getExitCode()));
	}

	// Batch stores local times in the server's zone
	private static java.time.Instant instant(LocalDateTime time) {
		return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
	}

}
