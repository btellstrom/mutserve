package genepi.mut.pileup;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

import genepi.base.Tool;
import genepi.io.FileUtil;
import genepi.io.text.LineWriter;
import genepi.mut.objects.BasePosition;
import genepi.mut.objects.VariantLine;
import genepi.mut.objects.VariantResult;
import genepi.mut.util.FastaWriter;
import genepi.mut.util.ReferenceUtil;
import genepi.mut.util.ReferenceUtil.Reference;
import genepi.mut.util.VariantCaller;
import genepi.mut.util.VcfWriter;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;

public class PileupToolLocal extends Tool {

	String version = "v1.1.18";
	String mode = "mtdna";
	String command;

	public PileupToolLocal(String[] args) {
		super(args);
		command = Arrays.toString(args);
		System.out.println("Command " + command);
	}

	@Override
	public void createParameters() { 

		addParameter("input", "input cram/bam file or folder", Tool.STRING);
		addParameter("output", "output file", Tool.STRING);
		addOptionalParameter("level", "detection level", Tool.STRING);
		addParameter("reference", "reference as fasta", Tool.STRING);
		addOptionalParameter("baseQ", "base quality", Tool.STRING);
		addOptionalParameter("mapQ", "mapping quality", Tool.STRING);
		addOptionalParameter("alignQ", "alignment quality", Tool.STRING);
		addOptionalParameter("minCoverage", "minimal coverage for variants", Tool.STRING);
		addFlag("noBaq", "turn off BAQ");
		addFlag("deletions", "Call deletions");
		addFlag("insertions", "Call insertions (beta)");
		addFlag("writeFasta", "Write fasta");
	}

	@Override
	public void init() {
		System.out.println("mtDNA Low-frequency Variant Detection " + version);
		System.out.println("Division of Genetic Epidemiology - Medical University of Innsbruck");
		System.out.println("(c) Sebastian Schoenherr, Hansi Weissensteiner, Lukas Forer");
		System.out.println("");
	}

	@Override
	public int run() {

		String input = (String) getValue("input");

		String output = (String) getValue("output");

		boolean baq = !isFlagSet("noBaq");

		boolean deletions = isFlagSet("deletions");

		boolean insertions = isFlagSet("insertions");

		boolean writeFasta = isFlagSet("writeFasta");

		double level;
		if (getValue("level") == null) {
			level = 0.01;
		} else {
			level = Double.parseDouble((String) getValue("level"));
		}

		int baseQ;
		if (getValue("baseQ") == null) {
			baseQ = 20;
		} else {
			baseQ = Integer.parseInt((String) getValue("baseQ"));
		}

		int mapQ;
		if (getValue("mapQ") == null) {
			mapQ = 20;
		} else {
			mapQ = Integer.parseInt((String) getValue("mapQ"));
		}

		int alignQ;
		if (getValue("alignQ") == null) {
			alignQ = 30;
		} else {
			alignQ = Integer.parseInt((String) getValue("alignQ"));
		}

		int minCoverage;
		if (getValue("minCoverage") == null) {
			minCoverage = 30;
		} else {
			minCoverage = Integer.parseInt((String) getValue("minCoverage"));
		}

		String refPath = (String) getValue("reference");

		LineWriter writerRaw = null;

		LineWriter writerVar = null;

		File folderIn = new File(input);
		File[] files;

		if (folderIn.exists()) {

			if (folderIn.isFile()) {
				files = new File[1];
				files[0] = new File(folderIn.getAbsolutePath());

				if (!files[0].getName().toLowerCase().endsWith(".cram")
						&& !files[0].getName().toLowerCase().endsWith(".bam")) {
					System.out.println("Please upload a CRAM/BAM file");
					return 1;
				}

			} else {
				files = folderIn.listFiles(new FilenameFilter() {
					public boolean accept(File dir, String name) {
						return name.toLowerCase().endsWith(".bam") || name.toLowerCase().endsWith(".cram");
					}
				});
			}
		} else {
			System.out.println("Please check input path!");
			return 1;
		}

		if (files.length > 0) {

			File out = new File(output);

			if (out.isDirectory()) {
				System.out.println("Error. Please specify an output file not a directory");
				return 1;
			}

			String prefix = output;

			if (output.contains(".")) {
				prefix = output.substring(0, output.indexOf('.'));
			}

			String varFile = prefix + ".txt";
			String rawFile = prefix + "_raw.txt";

			try {
				writerVar = new LineWriter(new File(varFile).getAbsolutePath());
				writerVar.write(BamAnalyser.headerVariants);

				writerRaw = new LineWriter(new File(rawFile).getAbsolutePath());
				writerRaw.write(BamAnalyser.headerRaw);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			long start = System.currentTimeMillis();

			System.out.println("Parameters:");
			System.out.println("Input: " + new File(input).getAbsolutePath());
			System.out.println("Output: " + new File(output).getAbsolutePath());
			System.out.println("Detection limit: " + level);
			System.out.println("Base Quality: " + baseQ);
			System.out.println("Map Quality: " + mapQ);
			System.out.println("Alignment Quality: " + alignQ);
			System.out.println("Minimal Variant Coverage: " + minCoverage);
			System.out.println("BAQ: " + baq);
			System.out.println("Deletions: " + deletions);
			System.out.println("Insertions: " + insertions);
			System.out.println("");

			for (File file : files) {

				Reference reference = ReferenceUtil.determineReference(file);

				if (reference == Reference.hg19) {

					System.out.println("File " + file.getName()
							+ " excluded! File is aligned to Yoruba (Reference length 16571) and not rCRS. ");

					continue;

				}

				else if (reference == Reference.rcrs || reference == Reference.precisionId) {

					BamAnalyser analyser = new BamAnalyser(file.getName(), refPath, baseQ, mapQ, alignQ, baq,
							minCoverage, mode);

					System.out.println("Processing: " + file.getName());
					System.out.println("Detected reference: " + reference.toString());

					try {

						analyseReads(file, analyser, deletions, insertions);

						determineVariants(analyser, writerRaw, writerVar, level);

					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						return 1;
					}

				} else {
					System.out.println(
							"File " + file.getName() + " excluded. Can not identify a valid reference length!");
					continue;
				}
			}

			try {
				writerVar.close();

				if (writerRaw != null) {
					writerRaw.close();
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (output.endsWith("vcf.gz") || output.endsWith("vcf")) {
				VcfWriter writer = new VcfWriter();
				writer.createVCF(varFile, output, refPath, "chrM", 16569, version + ";" + command);
			}

			if (writeFasta) {
				FastaWriter writer2 = new FastaWriter();
				writer2.createFasta(varFile, prefix + ".fasta", refPath);
			}

			System.out.println("Time: " + (System.currentTimeMillis() - start) / 1000 + " sec");
			return 0;

		}

		return 0;
	}

	// mapper
	private void analyseReads(File file, BamAnalyser analyser, boolean deletions, boolean insertions) throws Exception {

		// TODO double check if primary and secondary alignment is used for
		// CNV-Server
		final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
				.open(file);

		SAMRecordIterator fileIterator = reader.iterator();

		while (fileIterator.hasNext()) {

			SAMRecord record = fileIterator.next();

			analyser.analyseRead(record, deletions, insertions);

		}
		reader.close();
	}

	// reducer
	private void determineVariants(BamAnalyser analyser, LineWriter writerRaw, LineWriter writerVariants, double level)
			throws IOException {

		HashMap<String, BasePosition> counts = analyser.getCounts();

		String reference = analyser.getReferenceString();

		for (String key : counts.keySet()) {

			String idKey = key.split(":")[0];

			String positionKey = key.split(":")[1];

			int pos;

			boolean insertion = false;

			if (positionKey.contains(".")) {
				pos = Integer.valueOf(positionKey.split("\\.")[0]);
				insertion = true;
			} else {
				pos = Integer.valueOf(positionKey);
			}

			if (pos > 0 && pos <= reference.length()) {

				char ref = 'N';

				BasePosition basePos = counts.get(key);
				basePos.setId(idKey);

				basePos.setPos(pos);

				VariantLine line = new VariantLine();

				if (!insertion) {

					ref = reference.charAt(pos - 1);

				} else {

					ref = '-';

					line.setInsertion(true);

					line.setInsPosition(positionKey);
				}

				line.setRef(ref);

				// create all required frequencies for one position
				// applies checkBases()
				line.parseLine(basePos, level);

				boolean isHeteroplasmy = false;

				for (char base : line.getMinors()) {

					// this only works since minorFWD and minorREV are equal
					double minorPercentageFwd = VariantCaller.getMinorPercentageFwd(line, base);

					double minorPercentageRev = VariantCaller.getMinorPercentageRev(line, base);

					double llrFwd = VariantCaller.determineLlrFwd(line, base);

					double llrRev = VariantCaller.determineLlrRev(line, base);

					VariantResult varResult = VariantCaller.determineLowLevelVariant(line, minorPercentageFwd,
							minorPercentageRev, llrFwd, llrRev, level, base);

					if (varResult.getType() == VariantCaller.LOW_LEVEL_VARIANT) {

						isHeteroplasmy = true;

						// set correct minor base for output result!
						varResult.setMinor(base);

						double hetLevel = VariantCaller.calcVariantLevel(line, minorPercentageFwd, minorPercentageRev);

						double levelTop = VariantCaller.calcLevelTop(line);

						double levelMinor = VariantCaller.calcLevelMinor(line, minorPercentageFwd, minorPercentageRev);

						varResult.setLevelTop(levelTop);

						varResult.setLevelMinor(levelMinor);

						varResult.setLevel(hetLevel);

						String res = VariantCaller.writeVariant(varResult);

						writerVariants.write(res);
					}
				}

				if (!isHeteroplasmy) {

					VariantResult varResult = VariantCaller.determineVariants(line, analyser.getMinCoverage());

					if (varResult != null) {

						double hetLevel = VariantCaller.calcVariantLevel(line, line.getMinorBasePercentsFWD(),
								line.getMinorBasePercentsREV());

						double levelTop = VariantCaller.calcLevelTop(line);

						double levelMinor = VariantCaller.calcLevelMinor(line, line.getMinorBasePercentsFWD(),
								line.getMinorBasePercentsREV());

						varResult.setLevelTop(levelTop);

						varResult.setLevelMinor(levelMinor);

						varResult.setLevel(hetLevel);

						String res = VariantCaller.writeVariant(varResult);
						
						System.out.println(res);

						writerVariants.write(res);
					}
				}
				// raw data
				if (writerRaw != null) {
					String raw = line.toRawString();
					writerRaw.write(raw);
				}

			}
		}
	}

	public static void main(String[] args) {
		String input = "test-data/mtdna/bams";
		String output = "test-data/out.txt";
		String fasta = "test-data/mtdna/bam/reference/rCRS.fasta";

		PileupToolLocal pileup = new PileupToolLocal(new String[] { "--input", input, "--reference", fasta, "--output",
				output, "--level", "0.01", "--minCoverage", "30", "--deletions", "--insertions", "--writeFasta" });

		pileup.start();

	}

}
