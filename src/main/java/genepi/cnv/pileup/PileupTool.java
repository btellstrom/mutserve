package genepi.cnv.pileup;

import genepi.cnv.util.HadoopJobStep;
import genepi.hadoop.HdfsUtil;
import genepi.hadoop.PreferenceStore;
import genepi.hadoop.common.WorkflowContext;
import genepi.hadoop.io.HdfsLineWriter;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class PileupTool extends HadoopJobStep {

	@Override
	public boolean run(WorkflowContext context) {

		String type = context.get("inType");
		
		final String folder = getFolder(PileupTool.class);

		String input;
		if (type.equals("se") || type.equals("pe")) {
			input = context.get("outputBam");
		} else {
			input = context.get("input");
		}
		String output = context.get("analyseOut");
		String mappingQual = context.get("mapQuality");
		String baseQual = context.get("baseQuality");
		String alignQual = context.get("alignQuality");
		String stats = context.get("statistics");
		String reference = context.get("reference");
		Boolean baq = Boolean.valueOf(context.get("baq"));
		
		PileupJob bamJob = new PileupJob("Analyse BAM"){
			@Override
			protected void readConfigFile() {
				File file = new File(folder + "/" + CONFIG_FILE);
				if (file.exists()) {
					log.info("Loading distributed configuration file " + folder + "/" + CONFIG_FILE + "...");
					PreferenceStore preferenceStore = new PreferenceStore(file);
					preferenceStore.write(getConfiguration());
					for (Object key : preferenceStore.getKeys()) {
						log.info("  " + key + ": " + preferenceStore.getString(key.toString()));
					}

				} else {

					log.info("No distributed configuration file (" + CONFIG_FILE + ") available.");

				}
			}
		};
		bamJob.setInput(input);
		bamJob.setOutput(output);
		bamJob.setMappingQuality(mappingQual);
		bamJob.setBaseQuality(baseQual);
		bamJob.setAlignmentQuality(alignQual);
		bamJob.setBAQ(baq);
		bamJob.setReference(reference);
		bamJob.setJarByClass(PileupTool.class);
		bamJob.setFolder(folder);

		boolean successful = executeHadoopJob(bamJob, context);

		if (successful) {

			// print qc statistics
			DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.US);

			StringBuffer text = new StringBuffer();

			text.append("<b> Statistics:</b> <br>");
			text.append("Overall Reads: " + df.format(bamJob.getOverall()) + "<br>");
			text.append("Filtered Reads: " + df.format(bamJob.getFiltered()) + "<br>");
			text.append("Passed Reads: " + df.format(bamJob.getUnfiltered()) + "<br>");
			text.append("<br>");
			text.append("Read Mapping Quality OK: " + df.format(bamJob.getGoodMapping()) + "<br>");
			text.append("Read Mapping Quality BAD: " + df.format(bamJob.getBadMapping()) + "<br>");
			text.append("Unmapped Reads: " + df.format(bamJob.getUnmapped()) + "<br>");
			text.append("Wrong Reference in BAM: " + df.format(bamJob.getWrongRef()) + "<br>");
			text.append("Bad Alignment: " + df.format(bamJob.getBadALigment()) + "<br>");
			text.append("Duplicates: " + df.format(bamJob.getDupl()) + "<br>");
			text.append("Short Reads (<25 bp): " + df.format(bamJob.getShortRead()) + "<br>");
			context.ok(text.toString());

			if (bamJob.getUnfiltered() == 0 || bamJob.getGoodQual() == 0) {
				context.error("No reads passed Quality Control!");
				return false;
			}
			
			try {
				HdfsLineWriter logWriter = new HdfsLineWriter(HdfsUtil.path(stats));
				logWriter.write("BAM File Statistics ");
				logWriter.write("Overall Reads\t" + bamJob.getOverall());
				logWriter.write("Filtered Reads\t " + bamJob.getFiltered());
				logWriter.write("Passed Reads\t " + bamJob.getUnfiltered());
				logWriter.write("Read Mapping Quality OK\t " + bamJob.getGoodMapping());
				logWriter.write("Read Mapping Quality BAD\t " + bamJob.getBadMapping());
				logWriter.write("Unmapped Reads\t " + bamJob.getUnmapped());
				logWriter.write("Wrong Reference in BAM\t " + bamJob.getWrongRef());
				logWriter.write("Bad Alignment\t " + bamJob.getBadALigment());
				logWriter.write("Duplicates\t " + bamJob.getDupl());
				logWriter.write("Short Reads (<25 bp)\t " + bamJob.getShortRead());

				logWriter.write("");
				logWriter.close();
				

			} catch (IOException e) {
				e.printStackTrace();
			}

		} else {

			context.error("QC Quality Control failed!");
			return false;

		}
		return successful;

	}
	

}