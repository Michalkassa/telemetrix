package com.telemetrix.ingestion.history;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;

// Reads a dump CSV file as rows of column name -> value. Columns are found by the header line,
// never by position: Jolpica doesn't promise a stable column order.
final class DumpCsv {

	private DumpCsv() {
	}

	/** A restartable Spring Batch reader; `name` keys its progress in the step's saved state. */
	static FlatFileItemReader<Map<String, String>> reader(Path file, String name) {
		var tokenizer = new DelimitedLineTokenizer();
		return new FlatFileItemReaderBuilder<Map<String, String>>()
				.name(name)
				.resource(new FileSystemResource(file))
				.encoding("UTF-8")
				.linesToSkip(1)
				.skippedLinesCallback(header -> tokenizer.setNames(tokenizer.tokenize(header.replace("﻿", "")).getValues()))
				.lineTokenizer(tokenizer)
				.fieldSetMapper(fields -> {
					Map<String, String> row = new HashMap<>();
					String[] names = fields.getNames();
					String[] values = fields.getValues();
					for (int i = 0; i < names.length; i++) {
						row.put(names[i], values[i]);
					}
					return row;
				})
				.build();
	}

	/** Every row of a (small) file at once. */
	static List<Map<String, String>> readAll(Path file) {
		var reader = reader(file, "readAll");
		reader.open(new ExecutionContext());
		try {
			List<Map<String, String>> rows = new ArrayList<>();
			Map<String, String> row;
			while ((row = reader.read()) != null) {
				rows.add(row);
			}
			return rows;
		}
		catch (Exception e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
		finally {
			reader.close();
		}
	}

}
