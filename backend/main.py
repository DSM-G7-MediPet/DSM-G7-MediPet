from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional

app = FastAPI(title="MediPet Disease API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class Disease(BaseModel):
    id: str
    nombre: str
    especie: str
    sintomas: List[str]
    descripcion: str
    recomendacion: str


DISEASES: List[Disease] = [
    # ── Perro ──────────────────────────────────────────────────────────────────
    Disease(
        id="parvo_perro",
        nombre="Parvovirus Canino",
        especie="perro",
        sintomas=["vómitos intensos", "diarrea con sangre", "letargia severa", "fiebre alta", "pérdida de apetito"],
        descripcion="Enfermedad viral altamente contagiosa que destruye las células del intestino delgado y médula ósea. Más grave en cachorros menores de 6 meses y perros no vacunados.",
        recomendacion="EMERGENCIA VETERINARIA. Requiere hospitalización inmediata con fluidos IV, antieméticos y antibióticos para infecciones secundarias. La vacunación es la única prevención eficaz.",
    ),
    Disease(
        id="distemper_perro",
        nombre="Distemper Canino (Moquillo)",
        especie="perro",
        sintomas=["fiebre", "secreción nasal y ocular", "tos", "convulsiones", "temblores musculares"],
        descripcion="Enfermedad viral sistémica que afecta el sistema respiratorio, digestivo y nervioso central. Altamente contagiosa y con alta mortalidad en cachorros.",
        recomendacion="No tiene cura específica. Tratamiento de soporte intensivo. La vacuna DAP/DHPP es esencial. Evitar contacto con perros no vacunados.",
    ),
    Disease(
        id="leptospirosis_perro",
        nombre="Leptospirosis Canina",
        especie="perro",
        sintomas=["fiebre súbita", "vómitos", "ictericia (ojos amarillos)", "dolor muscular", "orina oscura", "debilidad"],
        descripcion="Infección bacteriana zoonótica transmitida por contacto con agua contaminada con orina de animales infectados. Puede transmitirse a humanos.",
        recomendacion="Requiere atención veterinaria urgente. Antibióticos (penicilina o doxiciclina). Use guantes al manipular al animal. Vacuna disponible contra las cepas más comunes.",
    ),
    Disease(
        id="hepatitis_perro",
        nombre="Hepatitis Infecciosa Canina",
        especie="perro",
        sintomas=["fiebre", "vómitos", "dolor abdominal", "ictericia", "opacidad corneal ('ojo azul')"],
        descripcion="Enfermedad viral causada por adenovirus canino tipo 1. Afecta principalmente el hígado, riñones y vasos sanguíneos. El 'ojo azul' es un signo característico.",
        recomendacion="Tratamiento de soporte hospitalario. La vacuna DAP/DHPP la previene. Cuarentena del animal afectado ya que el virus se excreta en orina hasta 6 meses.",
    ),
    Disease(
        id="ehrlichiosis_perro",
        nombre="Ehrlichiosis Canina (Fiebre de las Garrapatas)",
        especie="perro",
        sintomas=["fiebre", "pérdida de apetito", "sangrado espontáneo", "ganglios inflamados", "pérdida de peso"],
        descripcion="Enfermedad bacteriana transmitida por garrapatas. Puede volverse crónica y causar anemia severa si no se trata a tiempo.",
        recomendacion="Tratamiento con doxiciclina por 4-6 semanas. Control preventivo de garrapatas (pipetas, collares antiparasitarios). Análisis sanguíneos periódicos.",
    ),
    # ── Gato ───────────────────────────────────────────────────────────────────
    Disease(
        id="leucemia_gato",
        nombre="Leucemia Felina (FeLV)",
        especie="gato",
        sintomas=["pérdida de peso progresiva", "anemia", "infecciones frecuentes", "linfomas", "letargia crónica"],
        descripcion="Enfermedad viral que suprime el sistema inmunológico. Se transmite por contacto cercano con saliva y leche de gatos infectados. Incurable.",
        recomendacion="Manejo paliativo para mantener calidad de vida. Mantener al gato en interior para proteger a otros. Vacuna disponible para gatos en riesgo.",
    ),
    Disease(
        id="panleuco_gato",
        nombre="Panleucopenia Felina (Parvovirus Felino)",
        especie="gato",
        sintomas=["vómitos severos", "diarrea con sangre", "fiebre alta", "letargia extrema", "deshidratación rápida"],
        descripcion="Altamente contagiosa y frecuentemente fatal, especialmente en gatitos. Destruye células de médula ósea e intestino. Puede sobrevivir años en el ambiente.",
        recomendacion="EMERGENCIA VETERINARIA. Requiere hospitalización con fluidos IV. La vacuna FVRCP es altamente efectiva desde las primeras semanas de vida.",
    ),
    Disease(
        id="herpes_gato",
        nombre="Rinotraqueítis Viral Felina (Herpesvirus)",
        especie="gato",
        sintomas=["estornudos frecuentes", "secreción nasal", "conjuntivitis", "úlceras corneales", "fiebre"],
        descripcion="Infección viral del tracto respiratorio superior muy común. El virus puede permanecer latente y reactivarse con el estrés. Principal causa de 'gripe felina'.",
        recomendacion="Tratamiento antiviral (aciclovir ocular si hay úlceras), apoyo nutricional. La vacuna FVRCP reduce la severidad aunque no elimina el virus.",
    ),
    Disease(
        id="calici_gato",
        nombre="Calicivirus Felino",
        especie="gato",
        sintomas=["úlceras en boca y lengua", "estornudos", "secreción nasal", "cojera", "fiebre"],
        descripcion="Infección viral respiratoria y oral muy común. Las úlceras bucales dificultan la alimentación. Algunas cepas son especialmente virulentas y pueden ser fatales.",
        recomendacion="Soporte: analgésicos para las úlceras, alimentación suave o asistida. Aislamiento del animal. La vacuna FVRCP es preventiva.",
    ),
    Disease(
        id="renal_gato",
        nombre="Enfermedad Renal Crónica (ERC)",
        especie="gato",
        sintomas=["sed excesiva", "orinar en exceso o muy poco", "pérdida de peso", "vómitos frecuentes", "pérdida de apetito"],
        descripcion="Deterioro progresivo e irreversible de la función renal. Muy común en gatos mayores de 7 años. Manejable para mantener buena calidad de vida.",
        recomendacion="Dieta renal especial baja en fósforo y proteína. Fluidoterapia subcutánea en casa. Controles sanguíneos y de orina periódicos. Sin cura pero manejable.",
    ),
    # ── Conejo ─────────────────────────────────────────────────────────────────
    Disease(
        id="mixomatosis_conejo",
        nombre="Mixomatosis",
        especie="conejo",
        sintomas=["inflamación de párpados", "hinchazón genital", "secreción ocular purulenta", "dificultad respiratoria", "fiebre alta"],
        descripcion="Enfermedad viral grave y frecuentemente fatal. Transmitida por mosquitos, pulgas y contacto directo. Muy prevalente en conejos domésticos de exterior.",
        recomendacion="Sin tratamiento efectivo; tasa de mortalidad muy alta. VACUNE anualmente. Proteja con mosquiteras. Si hay síntomas, consulte al veterinario de inmediato.",
    ),
    Disease(
        id="rhd_conejo",
        nombre="Enfermedad Hemorrágica Viral (RHD1 y RHD2)",
        especie="conejo",
        sintomas=["muerte súbita sin síntomas previos", "sangrado nasal", "fiebre alta", "convulsiones", "letargia extrema"],
        descripcion="Enfermedad viral altamente contagiosa y mortal. Existen dos cepas (RHD1 y RHD2). El virus sobrevive semanas en el ambiente. Mortalidad casi del 100%.",
        recomendacion="Sin tratamiento efectivo. Vacune ANUALMENTE contra ambas cepas. Cuarentena estricta si hay sospecha. No introduzca conejos nuevos sin cuarentena previa.",
    ),
    Disease(
        id="pasteurelosis_conejo",
        nombre="Pasteurelosis (Snuffles)",
        especie="conejo",
        sintomas=["secreción nasal crónica", "estornudos frecuentes", "tortícolis (cabeza inclinada)", "abscesos subcutáneos", "infecciones de oído"],
        descripcion="Infección bacteriana crónica muy común. Pasteurella multocida puede causar múltiples problemas: respiratorios, neurológicos y abscesos en todo el cuerpo.",
        recomendacion="Antibióticos según cultivo y antibiograma (enrofloxacina, azitromicina). Abscesos requieren cirugía. Difícil de erradicar; el manejo a largo plazo es clave.",
    ),
    Disease(
        id="coccidiosis_conejo",
        nombre="Coccidiosis",
        especie="conejo",
        sintomas=["diarrea (a veces con sangre)", "distensión abdominal", "pérdida de peso rápida", "letargia", "pelaje opaco"],
        descripcion="Infección parasitaria causada por coccidios (Eimeria spp.) que afecta intestinos o hígado. Muy común en gazapos y conejos jóvenes o estresados.",
        recomendacion="Toltrazurilo o sulfamidas por 5-7 días. Limpieza exhaustiva del alojamiento con agua hirviendo. Separar a los enfermos. Higiene preventiva rigurosa.",
    ),
    Disease(
        id="sarna_conejo",
        nombre="Sarna (Sarcoptes y Notoedres)",
        especie="conejo",
        sintomas=["picazón intensa", "costras gruesas en orejas, nariz y patas", "pérdida de pelo", "heridas por rascado", "irritación de piel"],
        descripcion="Infestación por ácaros microscópicos que causan intensa picazón y lesiones cutáneas progresivas. Puede debilitar severamente al animal si no se trata.",
        recomendacion="Ivermectina inyectable o spot-on cada 2 semanas por 3 aplicaciones. Desinfectar y limpiar el alojamiento. Tratar a todos los conejos en contacto.",
    ),
]

ESPECIES = ["perro", "gato", "conejo"]


@app.get("/")
def root():
    return {"status": "ok", "service": "MediPet Disease API"}


@app.get("/especies", response_model=List[str])
def get_especies():
    return ESPECIES


@app.get("/enfermedades", response_model=List[Disease])
def get_enfermedades(especie: Optional[str] = None):
    if especie is None:
        return DISEASES
    especie_lower = especie.lower().strip()
    if especie_lower not in ESPECIES:
        raise HTTPException(status_code=404, detail=f"Especie '{especie}' no encontrada. Disponibles: {ESPECIES}")
    return [d for d in DISEASES if d.especie == especie_lower]


@app.get("/enfermedades/{disease_id}", response_model=Disease)
def get_enfermedad(disease_id: str):
    for d in DISEASES:
        if d.id == disease_id:
            return d
    raise HTTPException(status_code=404, detail=f"Enfermedad con id '{disease_id}' no encontrada")
