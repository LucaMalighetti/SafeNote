const express = require('express');
const bodyParser = require('body-parser');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const cors = require('cors');
const nodemailer = require('nodemailer');

const app = express();
const PORT = process.env.PORT || 3000;
const USERS_FILE = 'users.json';
const PHOTOS_FILE = 'photos.json';
const REQUESTS_FILE = 'requests.json';

// --- CONFIGURAZIONE EMAIL ---
const transporter = nodemailer.createTransport({
    service: 'gmail',
    auth: {
        user: 'malighettiluca08@gmail.com',
        pass: 'fowc ccjg dulh hfsi'
    }
});

app.use(cors());
app.use(bodyParser.json());
app.use('/uploads', express.static('uploads'));

// Funzione salvataggio
function saveData(file, data) {
    fs.writeFileSync(file, JSON.stringify(data, null, 2));
}

// Funzione caricamento
function loadData(file) {
    if (fs.existsSync(file)) {
        try { return JSON.parse(fs.readFileSync(file)); } catch (e) { return []; }
    }
    return [];
}

// --- PERSISTENZA DATI ---
if (!fs.existsSync('uploads')) fs.mkdirSync('uploads');

let users = loadData(USERS_FILE);
let photos = loadData(PHOTOS_FILE);
let requests = loadData(REQUESTS_FILE);
let pendingAuths = new Map();

// --- API AUTHENTICATION ---

app.post('/auth/login-direct', (req, res) => {
    const { email, password } = req.body;
    console.log(`[LOGIN] Tentativo: ${email}`);
    const user = users.find(u => u.email.toLowerCase() === email.toLowerCase() && u.password === password);
    if (user) {
        console.log(`[LOGIN] Ok: ${user.username}`);
        res.json({ success: true, username: user.username, className: user.className });
    } else {
        res.status(401).json({ success: false, message: 'Credenziali errate' });
    }
});

app.post('/auth/request-code', async (req, res) => {
    const { email, action, username, password, className } = req.body;
    const code = Math.floor(100000 + Math.random() * 900000).toString();
    pendingAuths.set(email.toLowerCase(), { code, action, userData: { username, password, className }, expires: Date.now() + 600000 });

    try {
        await transporter.sendMail({
            from: '"SafeNote Security" <malighettiluca08@gmail.com>',
            to: email,
            subject: `Codice SafeNote: ${code}`,
            text: `Il tuo codice di verifica: ${code}`
        });
        console.log(`[EMAIL] Inviata a ${email}`);
        res.json({ success: true });
    } catch (error) {
        console.error(`[EMAIL ERROR] ${error.message}`);
        // Fallback: se l'invio mail fallisce, stampiamo il codice nei log per permettere il test
        console.log(`[FALLBACK] Codice per ${email}: ${code}`);
        res.status(500).json({ success: false, message: 'Errore invio mail: ' + error.message });
    }
});

app.post('/auth/verify-code', (req, res) => {
    const { email, code } = req.body;
    const auth = pendingAuths.get(email.toLowerCase());
    if (!auth || auth.code !== code || auth.expires < Date.now()) return res.status(400).json({ message: 'Codice errato' });

    if (auth.action === 'register') {
        const newUser = { email: email.toLowerCase(), username: auth.userData.username, password: auth.userData.password, className: auth.userData.className };
        users.push(newUser);
        saveData(USERS_FILE, users);
        pendingAuths.delete(email.toLowerCase());
        return res.json({ success: true, username: newUser.username, className: newUser.className });
    }
    pendingAuths.delete(email.toLowerCase());
    res.json({ success: true });
});

// --- API FOTO ---

const storage = multer.diskStorage({
    destination: 'uploads/',
    filename: (req, file, cb) => cb(null, Date.now() + '-' + file.originalname)
});
const upload = multer({ storage: storage });

app.get('/photos/:className', (req, res) => {
    res.json(photos.filter(p => p.className === req.params.className));
});

app.post('/photos', upload.array('photos'), (req, res) => {
    const { owner, className, title, description, tags } = req.body;
    const newPhoto = {
        id: Date.now().toString(),
        ownerName: owner,
        className: className,
        title, description,
        tags: JSON.parse(tags),
        uriStrings: req.files.map(f => `http://${req.hostname}:${PORT}/uploads/${f.filename}`),
        photoCount: req.files.length,
        coverUriString: req.files[0] ? `http://${req.hostname}:${PORT}/uploads/${req.files[0].filename}` : null
    };
    photos.push(newPhoto);
    saveData(PHOTOS_FILE, photos);
    res.json(newPhoto);
});

// --- API RICHIESTE ---

app.post('/requests', async (req, res) => {
    const request = { ...req.body, id: Date.now().toString(), status: 'PENDING' };
    requests.push(request);
    saveData(REQUESTS_FILE, requests);

    const owner = users.find(u => u.username.toLowerCase() === request.ownerName.toLowerCase());
    if (owner) {
        try {
            await transporter.sendMail({
                from: '"SafeNote" <malighettiluca08@gmail.com>',
                to: owner.email,
                subject: 'Richiesta accesso appunti',
                text: `L'utente ${request.requesterName} vuole vedere i tuoi appunti.`
            });
        } catch (e) {}
    }
    res.json(request);
});

app.get('/requests/:username', (req, res) => {
    const u = req.params.username.toLowerCase();
    res.json(requests.filter(r => r.ownerName.toLowerCase() === u || r.requesterName.toLowerCase() === u));
});

app.put('/requests/:id', (req, res) => {
    const index = requests.findIndex(r => r.id === req.params.id);
    if (index !== -1) {
        requests[index].status = req.body.status;
        saveData(REQUESTS_FILE, requests);
        res.json(requests[index]);
    } else res.status(404).send('Not found');
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`Server SafeNote Online - RESET EFFETTUATO`);
});
