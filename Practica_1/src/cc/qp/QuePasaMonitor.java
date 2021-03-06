package cc.qp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import es.upm.babel.cclib.*;

public class QuePasaMonitor implements QuePasa {
	//ATRIBUTOS:
	//Atributo miembros:Mapa que tiene como clave el nombre del grupo(String) y como valor una lista con los id de los miembros del grupo (ArrayList<Integer>) 
	private Map<String, ArrayList<Integer>> miembros = new HashMap<String, ArrayList<Integer>>();
	//Atributo creador: Mapa que tiene como clave el nombre del grupo(String) y como valor el id del creador del grupo(int)
	private Map<String, Integer> creador = new HashMap<String, Integer>();
	//Atributo mensaje: Mapa que tiene como clave el id del usuario que lee el mensaje(int) 
	//y como valor una LIFO de mensajes(LinkedList<Mensaje>) 
	private Map<Integer, LinkedList<Mensaje>> mensaje = new HashMap<Integer, LinkedList<Mensaje>>();
	//Atributo conditions: Mapa que tiene como clave el id del usuario que lee el mensaje(int) 
	//y como valor una LIFO de condiciones(LinkedList<Monitor.Cond>)
	private Map<Integer, LinkedList<Monitor.Cond>> conditions = new HashMap<Integer, LinkedList<Monitor.Cond>>();
	//Monitor de exclusión mutua
	private Monitor mutex;

	public QuePasaMonitor() {
		mutex = new Monitor();
	}
	
	/**
	 * @param String  creadorUid
	 * @param String grupo
	 * Crea un grupo de QuePasa con el nombre de "grupo" cuyo creador tiene el id "creadorUid"
	 * @return void
	 * @throws PreconditionFailedException
	 */
	
	@Override
	public void crearGrupo(int creadorUid, String grupo) throws PreconditionFailedException {
		mutex.enter();
		//Si el grupo ya está creado salta una excepcion
		if (creador.containsKey(grupo)) {
			mutex.leave();
			throw new PreconditionFailedException();
		}
		creador.put(grupo, creadorUid);
		ArrayList<Integer> miembros_lista = new ArrayList<Integer>();
		miembros_lista.add(creadorUid);
		miembros.put(grupo, miembros_lista);
		if (mensaje.get(creadorUid) == null) {
			LinkedList<Mensaje> nuevo = new LinkedList<Mensaje>();
			mensaje.put(creadorUid, nuevo);
		}
		mutex.leave();

	}

	/**
	 * @param String creadorUid
	 * @param String grupo
	 * @param int nuevoMiembroUid
	 * El usuario "creadorUid" añade un nuevo miembro cuyo uid es "nuevoMiembroUid" al grupo 
	 * @return void
	 * @throws PreconditionFailedException
	 */

	@Override
	public void anadirMiembro(int creadorUid, String grupo, int nuevoMiembroUid) throws PreconditionFailedException {
		mutex.enter();
		//Si el creadorUid no es el creador del grupo o el nuevoMiembroUid ya esta en el grupo salta una excepcion
		if (!creador.containsValue(creadorUid) || miembros.get(grupo).contains(nuevoMiembroUid)) {
			mutex.leave();
			throw new PreconditionFailedException();
		}
		ArrayList<Integer> listaActualizada = miembros.get(grupo);
		listaActualizada.add(nuevoMiembroUid);
		miembros.remove(grupo);
		miembros.put(grupo, listaActualizada);
		LinkedList<Mensaje> nuevo = new LinkedList<Mensaje>();
		mensaje.put(nuevoMiembroUid, nuevo);
		mutex.leave();
	}
	/**
	 * @param String miembroUid
	 * @param String grupo
	 * El usuario "miembroUid" sale del grupo
	 * @return void
	 * @throws PreconditionFailedException
	 */
	@Override
	public void salirGrupo(int miembroUid, String grupo) throws PreconditionFailedException {
		mutex.enter();
		if ((creador.get(grupo) == null || miembros.get(grupo) == null)
				|| (!miembros.get(grupo).contains(miembroUid) && !creador.get(grupo).equals(miembroUid))) {
			mutex.leave();
			throw new PreconditionFailedException();
		}
		LinkedList<Mensaje> borrados = mensaje.get(miembroUid);
		for (int i = 0; i < borrados.size(); i++) {
			if (borrados.get(i).getGrupo().equals(grupo)) {
				borrados.remove(i);
			}
		}
		mensaje.remove(miembroUid);
		mensaje.put(miembroUid, borrados);
		ArrayList<Integer> listaActualizada = miembros.get(grupo);
		listaActualizada.remove((Object)miembroUid);
		miembros.remove(grupo);
		miembros.put(grupo, listaActualizada);
		mutex.leave();
	}
	/**
	 * @param int remitenteUid
	 * @param String grupo
	 * @param Object contenidos
	 * El usuario "remitenteUid" manda un mensaje "contenidos" por el grupo
	 * @return void
	 * @throws PreconditionFailedException
	 */
	@Override
	public void mandarMensaje(int remitenteUid, String grupo, Object contenidos) throws PreconditionFailedException {
		mutex.enter();
		if (miembros.get(grupo) == null || !miembros.get(grupo).contains(remitenteUid)) {
			mutex.leave();
			throw new PreconditionFailedException();
		}

		ArrayList<Integer> n_miembros = miembros.get(grupo);
		Mensaje msge = new Mensaje(remitenteUid, grupo, contenidos);
		for (int i = 0; i < n_miembros.size(); i++) {
			LinkedList<Mensaje> aux = mensaje.get(n_miembros.get(i));
			aux.addLast(msge);
			mensaje.put(n_miembros.get(i), aux);
			desbloquear(n_miembros.get(i));
		}
		mutex.leave();
	}
	/**
	 *	@param int uid
	 * 	Lee el primer mensaje disponible de lista de mensaje(uid)
	 * @return Mensaje
	 * @throws PreconditionFailedException
	 */
	@Override
	public Mensaje leer(int uid) {
		mutex.enter();

		if (mensaje.get(uid) == null || mensaje.get(uid).isEmpty()) {
			// Se crea la condicion y se almacena en el Map 
			Monitor.Cond aux = mutex.newCond();

			if (this.conditions.get(uid) == null) {
				LinkedList<Monitor.Cond> ConditionList = new LinkedList<Monitor.Cond>();
				ConditionList.addLast(aux);
				this.conditions.put(uid, ConditionList);


			} else {
				LinkedList<Monitor.Cond> ConditionList = this.conditions.get(uid);
				ConditionList.addLast(aux);
				this.conditions.remove(uid);
				this.conditions.put(uid, ConditionList);
			}

			this.conditions.get(uid).getLast().await();

			while(!this.conditions.get(uid).isEmpty() && this.conditions.get(uid)!=null && this.conditions.get(uid).getLast().waiting() > 0){
				desbloquear(uid);
			}

			if (this.conditions.get(uid).isEmpty()) {
				this.conditions.remove(uid);
			}
		}

		LinkedList<Mensaje> aux = mensaje.get(uid);
		Mensaje msge = aux.pop();
		mensaje.remove(uid);
		mensaje.put(uid, aux);
		mutex.leave();
		return msge;
	}
	/**
	 *	@param int uid
	 *  Desbloquea una condition de la lista conditions(uid) y luego elimina la condition 
	 * @return void
	 */
	public void desbloquear(int uid) {
		mutex.enter();
		if (!(conditions.get(uid) == null) && !conditions.get(uid).isEmpty()
				&& conditions.get(uid).getLast().waiting() > 0) {
			this.conditions.get(uid).pop().signal();
		}
		mutex.leave();
	}
}
