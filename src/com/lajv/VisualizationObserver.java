package com.lajv;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;

import com.lajv.NetworkNode;
import com.lajv.closepeer.ClosePeerProtocol;
import com.lajv.vivaldi.VivaldiProtocol;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;

public class VisualizationObserver implements Control {

	// ========================= Parameters ===============================
	// ====================================================================

	/**
	 * The vivaldi protocol to operate on.
	 * 
	 * @config
	 */
	private static final String PAR_VIVALDI_PROT = "vivaldi_protocol";

	/**
	 * The close peer protocol to operate on.
	 * 
	 * @config
	 */
	private static final String PAR_CLOSE_PEER_PROT = "closepeer_protocol";

	/**
	 * The node which will be choosen as the center. It has to be one of the initial nodes.
	 * 
	 * @config
	 */
	private static final String PAR_CENTER_NODE = "center_node";

	/**
	 * Config parameter that determines the threshold for when the Vivaldi algorithm should stop the
	 * execution. If not defined, a negative value is used which makes sure the observer does not
	 * stop the simulation.
	 * 
	 * @config
	 */
	private static final String PAR_THRESHOLD = "threshold";

	/**
	 * Config parameter which defines the prefix for where the coordinates are saved. Include a
	 * slash (/) for saving the files in a sub-directory. Default prefix is
	 * {@value #coords_file_prefix_default}.
	 * 
	 * @config
	 */
	private static final String PAR_COORDS_FILE_PREFIX = "coords_file_prefix";
	private static final String coords_file_prefix_default = "vivaldi-tmp/coordinates";

	/**
	 * Config parameter which defines the prefix for where the meta data is saved. Include a slash
	 * (/) for saving the files in a sub-directory. Default prefix is {@value #meta_file_default}.
	 * 
	 * @config
	 */
	private static final String PAR_META_FILE = "meta_file_prefix";
	private static final String meta_file_default = "./vivaldi-tmp/meta.csv";

	// =========================== Fields =================================
	// ====================================================================

	/**
	 * The name of this observer in the configuration. Initialized by the constructor parameter.
	 */
	private final String name;

	/**
	 * Protocol identifier; obtained from config property {@link #PAR_VIVALDI_PROT}.
	 * */
	private final int vivPid;

	/**
	 * Protocol identifier; obtained from config property {@link #PAR_CLOSE_PEER_PROT}.
	 * */
	private final int cpPid;

	/**
	 * Threshold for when to stop the execution; obtained from config property
	 * {@link #PAR_THRESHOLD}. The value is calculated as the sum of differences in distance in
	 * square.
	 * */
	private final double threshold;

	private double inital_sum, final_sum;
	private int iterations;
	private String coords_file_prefix;
	private String meta_file;
	private int centerNodeID;
	private double avg_distance = 0;

	// ==================== Constructor ===================================
	// ====================================================================

	public VisualizationObserver(String name) {
		this.name = name;
		vivPid = Configuration.getPid(name + "." + PAR_VIVALDI_PROT);
		cpPid = Configuration.getPid(name + "." + PAR_CLOSE_PEER_PROT);
		threshold = Configuration.getDouble(name + "." + PAR_THRESHOLD, -1);
		inital_sum = final_sum = iterations = 0;
		coords_file_prefix = Configuration.getString(name + "." + PAR_COORDS_FILE_PREFIX,
				coords_file_prefix_default);
		meta_file = Configuration.getString(name + "." + PAR_META_FILE, meta_file_default);
		centerNodeID = Configuration.getInt(name + "." + PAR_CENTER_NODE);

		// PrintStream ps;
		// try {
		// ps = new PrintStream(new FileOutputStream(meta_file));
		// ps.println("iteration, cycle, sum_errors, avg_err, avg_uncertainty, avg_uncertainty_balance, avg_move_distance");
		// ps.close();
		// } catch (FileNotFoundException e) {
		// e.printStackTrace();
		// }

	}

	// ====================== Methods =====================================
	// ====================================================================

	/**
	 * Prints the sum of errors calculated by the difference between real distances and estimated
	 * distance in square.
	 * 
	 * 
	 * @return if the sum of distances in square is less than the given {@value #PAR_THRESHOLD}.
	 */

	@Override
	public boolean execute() {
		double minError = 10000;
		double maxError = 0;
		double sum = 0;
		double avg_err = 0;
		double avg_norm_error = 0;
		double avg_uncertainty = 0;
		double avg_uncertainty_balance = 0;
		double avg_move_distance = 0;

		String coords_filename = coords_file_prefix + iterations + ".csv";
		try {
			FileOutputStream fos = new FileOutputStream(coords_filename);
			PrintStream ps = new PrintStream(fos);

			// Sort network and get center node
			Network.sort(null);
			NetworkNode me = (NetworkNode) Network.get(centerNodeID);
			double meColor = 0, nbColor = 0.5, otherColor = 1, color;
			ClosePeerProtocol cpp = (ClosePeerProtocol) me.getProtocol(cpPid);

			for (int i = 0; i < Network.size(); i++) {
				NetworkNode n1 = (NetworkNode) Network.get(i);
				VivaldiProtocol vp1 = (VivaldiProtocol) n1.getProtocol(vivPid);

				if (n1 == me)
					color = meColor;
				else if (cpp.isNb(n1))
					color = nbColor;
				else
					color = otherColor;

				ps.println(vp1.vivCoord.toCSV() + ", " + color);// + "," + ((IspLocation)
																// n1.location).isp);
				for (int j = i + 1; j < Network.size(); j++) {
					NetworkNode n2 = (NetworkNode) Network.get(j);
					VivaldiProtocol vp2 = (VivaldiProtocol) n2.getProtocol(vivPid);
					double latency = n1.location.latency(n2.location);
					double estLatency = vp1.vivCoord.distance(vp2.vivCoord);
					double error = Math.abs(latency - estLatency);
					minError = error < minError ? error : minError;
					maxError = error > maxError ? error : maxError;
					sum += error * error;
					avg_err += error;
				}
				avg_uncertainty += vp1.uncertainty;
				avg_uncertainty_balance += vp1.last_uncertainty_balance;
				avg_move_distance += vp1.last_move_distance;
			}
			long n = 0;
			for (int i = 1; i < Network.size(); i++) {
				n += i;
			}
			avg_err /= n;
			if (avg_distance == 0)
				avg_distance = avg_err;
			avg_norm_error = avg_err / avg_distance;
			avg_uncertainty /= Network.size();
			avg_uncertainty_balance /= Network.size();
			avg_move_distance /= Network.size();
			fos.close();

			// Write meta data
			BufferedWriter bw = new BufferedWriter(new FileWriter(new File(meta_file), true));
			bw.append("0, ");
			bw.append(iterations + ", ");
			bw.append(sum + ", ");
			bw.append(avg_err + ", ");
			bw.append(avg_norm_error + ", ");
			bw.append(avg_uncertainty + ", ");
			bw.append(avg_uncertainty_balance + ", ");
			bw.append(avg_move_distance + "\n");
			bw.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (iterations == 0)
			inital_sum = sum;

		if (iterations == Configuration.getInt("simulation.cycles") - 1) {
			final_sum = sum;
			System.out.println(name + ": Initial sum was " + inital_sum);
			System.out.println(name + ": Final sum is " + final_sum + " after " + iterations
					+ " cycles");
		}

		if (iterations % 100 == 0 || sum < threshold) {
			System.out.println(name + ": Sum is " + sum);
			System.out.println(name + ": Max error is " + maxError);
			System.out.println(name + ": Min error is " + minError);
			if (sum < threshold)
				System.out.println(name + ": Threshold reached after " + iterations + " cycles");
		}
		iterations++;

		/* Terminate if accuracy target is reached */
		return (sum < threshold && Network.size() != 0);
	}
}
