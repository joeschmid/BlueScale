/*
*  
* This file is part of BlueScale.
*
* BlueScale is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* BlueScale is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with BlueScale.  If not, see <http://www.gnu.org/licenses/>.
* 
* Copyright Vincent Marquez 2010
* 
* 
* Please contact us at www.BlueScaleSoftware.com
*
*/
package org.bluescale.telco.jainsip 
 
import org.bluescale.telco.jainsip._
import org.bluescale.telco._
import org.bluescale.telco.api._
import scala.collection.immutable.Map
import javax.sip.header._
import javax.sip._
import javax.sdp.SessionDescription
import javax.sip.message._

import org.bluescale.telco._
import org.bluescale.telco.Types._

import scala.actors.Actor
import scala.actors.Actor._
import org.bluescale.util._
import org.bluescale.telco.Types._

class JainSipConnection protected[telco](
                        var connid:String,
                        val to:String,
                        val from:String, 
                        val dir:DIRECTION, 
                        val telco:SipTelcoServer,
                        val disconnectOnUnjoin:Boolean)
                
                        extends SipConnection
                            with SipState
                     	    //with SipData
                     	    with LogHelper 
                     	    with Lockable 
                     	    with OrderedExecutable {
	  
    override def destination = to
    
    override def origin = from 
    
    private var _joinedTo:Option[Joinable[_]] = None // should be in the Joined trait
    
    override def joinedTo = _joinedTo
	
	override def connectionid = connid
 
	override def direction = dir
 
	override def protocol = "SIP"

	private var progressingCallback:Option[(SipConnection)=>Unit] = None 
  	
	protected[jainsip] def setConnectionid(id:String) = connid = id
 
 	override def connect( f:FinishFunction) = connect(SdpHelper.getJoinable(sdp), false, f)//shouldn't be this.  that's weird

 	override def connect(join:Joinable[_], callback:FinishFunction) : Unit = connect(join, false, callback) 

    protected[telco] override def connect(join:Joinable[_], connectAnyMedia:Boolean, callback:()=>Unit) = wrapLock {
        state match {
            case s:UNCONNECTED =>
                val t = telco.internal.sendInvite(this, join.sdp)
                //clientTx = Some(telco.internal.sendInvite(this, join.sdp)._1)
            case s:CONNECTED =>
                clientTx.foreach( tx =>
                    clientTx = Some(telco.internal.sendReinvite(tx, join.sdp) ))
        }
        clientTx.foreach( tx => {
            setRequestCallback(tx.getBranchId(), (responseCode, previousSdp) => {
                responseCode match {
                    case Response.RINGING =>
                        state = RINGING()
                        //only if it's different!
                        if (!previousSdp.toString().equals(sdp.toString()))
                            joinedTo.foreach( join => join.joinedMediaChange() )
                    case Response.OK =>
                        state = CONNECTED()
                        //only if it's different
                        this._joinedTo = Some(join)
                        if (!previousSdp.toString().equals(sdp.toString()))
                            joinedTo.foreach( join => join.joinedMediaChange() )
                        callback()
                }
            })
            progressingCallback.foreach( _(this) )
        })
    }

    override protected def loadInitialSdp() = 
        telco.silentSdp()
    
    private var callbacks = Map[String, (Int,SessionDescription)=>Unit]()

    private def setRequestCallback(branchId:String, f:(Int, SessionDescription)=>Unit) =
        callbacks += branchId->f
        
    
    override def joinedMediaChange() = wrapLock {
        joinedTo.foreach( join => connect(join,()=>{}) )
    }

  	override def join(otherCall:Joinable[_], joinCallback:FinishFunction) = wrapLock {
  	    //first, am I already joined? if so, we need to reconnect what i'm joined to. 
        val f = ()=>
            otherCall.connect(this, ()=> 
                connect(otherCall, joinCallback)
            )
        
  	    joinedTo match { 
            case Some(joined) => joined.connect(telco.silentJoinable(), f)
            case None => f()
  	    }
    }

    private def incomingResponse(responseCode:Int, toJoin:Joinable[_], connectedCallback:FinishFunction) = wrapLock {
        serverTx.foreach( tx => 
            connectionState match {
                case UNCONNECTED() | RINGING() =>
                    callbacks += tx.getBranchId()->((r,sdp) => connectedCallback )
		            telco.internal.sendResponse(200, tx, toJoin.sdp.toString().getBytes())  
	            case _ => 
	                throw new InvalidStateException(new UNCONNECTED(), connectionState)
	        })
    }
                
    override def accept(toJoin:Joinable[_], connectedCallback:FinishFunction) = 
        incomingResponse(200, toJoin, connectedCallback)

    override def accept(connectedCallback:FinishFunction) =
	    accept(SdpHelper.getBlankJoinable(telco.contactIp), connectedCallback)

    override def ring(toJoin:Joinable[_]) =
        incomingResponse(183, toJoin, ()=>{})
	
    override def ring() =
        incomingResponse(180, telco.silentJoinable(), ()=>{}) 
 
	override def reject(rejectCallback:FinishFunction) = 
	    incomingResponse(606, telco.silentJoinable(), rejectCallback)

	override def disconnect(disconnectCallback:FinishFunction) = wrapLock {
		clientTx.foreach( tx => {
		    /*telco.internal.sendByeRequest(this)*/
            setRequestCallback( tx.getBranchId(), (responseCode, sdp)=> {
                state = UNCONNECTED()
                onDisconnect()
                disconnectCallback()
            })
        })
        //setFinishFunction(VERSIONED_UNCONNECTED(clientTx.get.getBranchId()), f)
  	}

  	override def setUAC(clientTx:ClientTransaction, responseCode:Int, sdp:SessionDescription) = wrapLock {
        val f = callbacks(clientTx.getBranchId())
        callbacks = callbacks.filter( (kv) => clientTx.getBranchId() == kv._1)
        callbacks(clientTx.getBranchId())(responseCode, sdp) 
  	}

    def bye(tx:ServerTransaction) {
        state = UNCONNECTED()
        serverTx = Some(tx)
        joinedTo.foreach(join => join.unjoin(()=>Unit) )
        //set state to disconnected.
    }

    override def reinvite(tx:ServerTransaction, sdp:SessionDescription) {
        this.sdp = sdp//wtf?
        serverTx = Some(tx)
        joinedTo.foreach(join => join.joinedMediaChange())
    }

    override def invite(tx:ServerTransaction, sdp:SessionDescription) {
        this.sdp = sdp
        serverTx = Some(tx)
    }

    override def ack(tx:ServerTransaction) {
        //we're connected now...set state 
    }

  	override def cancel(f:FinishFunction) =  
  	    dir match {
            case INCOMING() => cancelIncoming(f)
            case OUTGOING() => cancelOutgoing(f)
      	}

  	protected def cancelIncoming(cancelCallback:FinishFunction) = wrapLock {
    }

  	protected def cancelOutgoing(cancelCallback:FinishFunction) = wrapLock {
 	}

    override def hold(f:FinishFunction) : Unit = wrapLock {
        /*
        joinedTo match {
            case None => this.reconnect(SdpHelper.getBlankJoinable(telco.contactIp), f)//silence(f)
            case Some(otherConn) => otherConn.connect(SdpHelper.getBlankJoinable(telco.contactIp), ()=>hold(f))
        }
        */
    }    

	override def unjoin(f:FinishFunction) = wrapLock {
	
	}
  
    protected def onDisconnect() = wrapLock {
        joinedTo.foreach( joined=>{
                _joinedTo = None 
                joined.unjoin(()=>Unit) //uuugh how did the ohter one get unjoined
         })
    }
       
    override def toString() = 
	    "JainSipConnection " + direction + " TO:"+destination + " State = " + state + " Hashcode = " + hashCode

}
 
