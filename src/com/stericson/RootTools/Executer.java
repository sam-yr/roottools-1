package com.stericson.RootTools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import android.util.Log;

public class Executer {

	// ------------
	// # Executer #
	// ------------

	/**
	 * Sends several shell command as su (attempts to)
	 * 
	 * @param commands
	 *            array of commands to send to the shell
	 * 
	 * @param sleepTime
	 *            time to sleep between each command, delay.
	 * 
	 * @param result
	 *            injected result object that implements the Result class
	 * 
	 * @return a <code>LinkedList</code> containing each line that was returned
	 *         by the shell after executing or while trying to execute the given
	 *         commands. You must iterate over this list, it does not allow
	 *         random access, so no specifying an index of an item you want, not
	 *         like you're going to know that anyways.
	 * 
	 * @throws InterruptedException
	 * 
	 * @throws IOException
	 * @throws TimeoutException 
	 */
	public List<String> sendShell(String[] commands, int sleepTime,
			IResult result, boolean useRoot, int timeout) throws IOException,
			RootToolsException, TimeoutException {

		RootTools.log(InternalVariables.TAG, "Sending " + commands.length
				+ " shell command" + (commands.length > 1 ? "s" : ""));

        Worker worker = new Worker(commands, sleepTime, result, useRoot);
        worker.start();
        
        try
        {
        	if (timeout == -1)
        	{
        		timeout = 300000;
        	}
        	
        	worker.join(timeout);
            if (worker.exit != -911)
              return worker.finalResponse;
            else
              throw new TimeoutException();
        } 
        catch(InterruptedException ex) 
        {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new TimeoutException();
        } 

	}

    private static class Worker extends Thread 
    {
    	private String[] commands;
    	private int sleepTime;
    	private IResult result;
    	private boolean useRoot;
    	public int exit = -911;
    	public List<String> finalResponse;
	  
		private Worker(String[] commands, int sleepTime, IResult result, boolean useRoot) 
		{
			this.commands = commands;
			this.sleepTime = sleepTime;
			this.result = result;
			this.useRoot = useRoot;
		}
		public void run() 
		{
			Process process = null;
			DataOutputStream os = null;
			InputStreamReader osRes = null;
			InputStreamReader osErr = null;
		    try 
		    { 
				process = Runtime.getRuntime().exec(useRoot ? "su" : "sh");
				RootTools.log(useRoot ? "Using Root" : "Using sh");
				if (null != result) {
					result.setProcess(process);
				}

				os = new DataOutputStream(process.getOutputStream());
				osRes = new InputStreamReader(process.getInputStream());
				osErr = new InputStreamReader(process.getErrorStream());
				BufferedReader reader = new BufferedReader(osRes);
				BufferedReader reader_error = new BufferedReader(osErr);

				List<String> response = null;

				if (null == result) {
					response = new LinkedList<String>();
				}

				try {
					
					// Doing Stuff ;)
					for (String single : commands) {
						os.writeBytes(single + "\n");
						os.flush();
						Thread.sleep(sleepTime);
					}

					os.writeBytes("exit \n");
					os.flush();

					String line = reader.readLine();
					String line_error = reader_error.readLine();

					while (line != null) {
						if (null == result) {
							response.add(line);
						} else {
							result.process(line);
						}

						RootTools.log("input stream" + line);
						line = reader.readLine();
					}

					while (line_error != null) {
						if (null == result) {
							response.add(line_error);
						} else {
							result.processError(line_error);
						}

						RootTools.log("error stream: " + line_error);
						line_error = reader_error.readLine();
					}

				} 
				catch (Exception ex) 
				{
					if (null != result) 
					{
						result.onFailure(ex);
					}
				} finally {
					if (process != null) {
						finalResponse = response;
						exit = process.waitFor();
						RootTools.lastExitCode = exit;
						
						if (null != result) {
							result.onComplete(exit);
						} else {
							response.add(Integer.toString(exit));
						}
					}

					try {
						if (os != null) {
							os.close();
						}
						if (osRes != null) {
							osRes.close();
						}
					} catch (Exception e) {
						Log.e(InternalVariables.TAG,
								"Catched Exception in finally block!");
						e.printStackTrace();
					}
				}
		    }
		    catch (InterruptedException ignore) 
		    {
		    	return;
		    }
		    catch (Exception e) {
	            if (RootTools.debugMode) {
	                RootTools.log("Error: " + e.getMessage());
	            }
	        } finally {
	            try {
	                if (os != null) {
	                    os.close();
	                }
	                if (osRes != null) {
	                    osRes.close();
	                }
	                //This was causing exceptions?
	                process.destroy();
	            } catch (Exception e) {
	                if (RootTools.debugMode) {
	                    RootTools.log("Error: " + e.getMessage());
	                }
	            }
	        }
		}
    }
}
