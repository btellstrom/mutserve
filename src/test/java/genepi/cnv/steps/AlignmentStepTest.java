package genepi.cnv.steps;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.Charsets;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.io.Files;
import com.google.common.io.LineReader;

import genepi.cnv.align.AlignTool;
import genepi.cnv.util.TestCluster;
import genepi.cnv.util.WorkflowTestContext;
import genepi.hadoop.HdfsUtil;
import genepi.hadoop.common.WorkflowStep;
import genepi.io.FileUtil;
import genepi.io.table.reader.CsvTableReader;

public class AlignmentStepTest {

	public static final boolean VERBOSE = true;

	@BeforeClass
	public static void setUp() throws Exception {
		TestCluster.getInstance().start();
	}

	@AfterClass
	public static void tearDown() throws Exception {
		TestCluster.getInstance().stop();
	}

	@Test
	public void AlignmentSETest() throws IOException {

		String inputFolder = "test-data/mtdna/fastqse/";
		String reference = "rcrs";
		String hdfsFolder = "inputSE";
		
		importInputdata(inputFolder,hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(inputFolder, reference);
		
		context.setInput("input", hdfsFolder);
		context.setInput("inType", "se");
		context.setOutput("bwaOut", "cloudgene-bwaOutSe");

		// create step instance
		AlignTool align = new AlignnMock("files");

		boolean result = align.run(context);

		assertTrue(result);

		HdfsUtil.merge(new File("test-data/tmp/bwaOut/result.txt").getPath(), "cloudgene-bwaOutSe/0", false);

		try (BufferedReader br = new BufferedReader(new FileReader(new File("test-data/tmp/bwaOut/result.txt")))) {
			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {

				if (line.length() > 0) {

					if (line.contains("QS6LK:01441:00464")) {
						assertEquals("23S25M1D48M1I93M", line.split("\t")[6]);
					}
					i++;
				}
			}
			br.close();
			///bwa mem rcrs.fasta small_small.fastq_| wc -l
			assertEquals(317, i);
			FileUtil.deleteDirectory("test/tmp");

		}
	}
	
	
	@Test
	public void Alignment2SETest() throws IOException {

		String inputFolder = "test-data/mtdna/fastqse/";
		String reference = "rcrs";
		String hdfsFolder = "inputSE";
		
		importInputdata(inputFolder,hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(inputFolder, reference);
		
		context.setInput("input", hdfsFolder);
		context.setInput("inType", "se");
		context.setOutput("bwaOut", "cloudgene-bwaOutSe");

		// create step instance
		AlignTool align = new AlignnMock("files");

		boolean result = align.run(context);

		assertTrue(result);

		HdfsUtil.merge(new File("test-data/tmp/bwaOut/result.txt").getPath(), "cloudgene-bwaOutSe/0", false);

		try (BufferedReader br = new BufferedReader(new FileReader(new File("test-data/tmp/bwaOut/result.txt")))) {
			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {

				if (line.length() > 0) {

					if (line.contains("QS6LK:01441:00464")) {
						assertEquals("23S25M1D48M1I93M", line.split("\t")[6]);
					}
					i++;
				}
			}
			br.close();
			///bwa mem rcrs.fasta small_small.fastq_| wc -l
			assertEquals(317, i);
			FileUtil.deleteDirectory("test/tmp");

		}
	}

	@Test
	public void AlignmentPETest() throws IOException {

		String inputFolder = "test-data/mtdna/fastqpe/";
		String reference = "rcrs";
		String hdfsFolder ="inputPE";
		
		importInputdata(inputFolder, hdfsFolder);

		// create workflow context
		WorkflowTestContext context = buildContext(inputFolder, reference);

		context.setInput("input", hdfsFolder);
		context.setInput("inType", "pe");
		context.setInput("chunkLength", "0");
		context.setOutput("bwaOut", "cloudgene-bwaOutPe");

		// create step instance
		AlignTool align = new AlignnMock("files");

		boolean result = align.run(context);

		assertTrue(result);

		HdfsUtil.merge(new File("test-data/tmp/bwaOut/result.txt").getPath(), "cloudgene-bwaOutPe/0", false);

		try (BufferedReader br = new BufferedReader(new FileReader(new File("test-data/tmp/bwaOut/result.txt")))) {
			String line;
			int i = 0;
			while ((line = br.readLine()) != null) {

				if (line.length() > 0) {
					System.out.println("." + line);
					i++;
				}

			}
			///bwa mem rcrs.fasta small_r1.fastq small_r2.fastq_small | wc -l
			// there is one additional line which is ignore
			assertEquals(200, i);
			System.out.println(i);
			br.close();
			FileUtil.deleteDirectory("test/tmp");

		}
	}

	class AlignnMock extends AlignTool {

		private String folder;

		public AlignnMock(String folder) {
			super();
			this.folder = folder;
		}

		@Override
		public String getFolder(Class clazz) {
			// override folder with static folder instead of jar location
			return folder;
		}

	}

	protected boolean run(WorkflowTestContext context, WorkflowStep step) {
		step.setup(context);
		return step.run(context);
	}

	protected WorkflowTestContext buildContext(String input, String ref) {

		File file = new File("test-data/tmp");
		if (file.exists()) {
			FileUtil.deleteDirectory(file);
		}
		file.mkdirs();

		WorkflowTestContext context = new WorkflowTestContext();

		context.setVerbose(VERBOSE);
		context.setInput("reference", ref);

		FileUtil.createDirectory(file.getAbsolutePath() + "/bwaOut");

		return context;

	}

	private void importInputdata(String folder, String input) {
		System.out.println("Import Data:");
		String[] files = FileUtil.getFiles(folder, "*.*");
		for (String file : files) {
			String target = HdfsUtil.path(input, FileUtil.getFilename(file));
			System.out.println("  Import " + file + " to " + target);
			HdfsUtil.put(file, target);
		}
	}

}