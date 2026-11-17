import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

interface Recurso { String id(); String provedor(); }
interface MaquinaVirtual extends Recurso { BigDecimal custoHora(); }
interface Armazenamento extends Recurso { BigDecimal custoGb(); }
interface Fila extends Recurso { BigDecimal custoFixo(); }

final class AwsVm implements MaquinaVirtual {
    private final String id;
    AwsVm(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Aws"; }
    public BigDecimal custoHora() { return new BigDecimal("0.10"); }
}
final class AwsStorage implements Armazenamento {
    private final String id;
    AwsStorage(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Aws"; }
    public BigDecimal custoGb() { return new BigDecimal("0.023"); }
}
final class AwsQueue implements Fila {
    private final String id;
    AwsQueue(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Aws"; }
    public BigDecimal custoFixo() { return new BigDecimal("0.40"); }
}
final class AzureVm implements MaquinaVirtual {
    private final String id;
    AzureVm(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Azure"; }
    public BigDecimal custoHora() { return new BigDecimal("0.12"); }
}
final class AzureStorage implements Armazenamento {
    private final String id;
    AzureStorage(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Azure"; }
    public BigDecimal custoGb() { return new BigDecimal("0.02"); }
}
final class AzureQueue implements Fila {
    private final String id;
    AzureQueue(String id) { this.id = id; }
    public String id() { return id; }
    public String provedor() { return "Azure"; }
    public BigDecimal custoFixo() { return new BigDecimal("0.50"); }
}
interface FabricaNuvem {
    String provedor();
    MaquinaVirtual criarVm();
    Armazenamento criarArmazenamento();
    Fila criarFila();
    BigDecimal custoHoraVm();
    BigDecimal custoGb();
    BigDecimal custoFila();
}
final class FabricaAws implements FabricaNuvem {
    private final AtomicInteger vm = new AtomicInteger(), storage = new AtomicInteger(), queue = new AtomicInteger();
    public String provedor() { return "Aws"; }
    public MaquinaVirtual criarVm() { return new AwsVm("i-" + vm.incrementAndGet()); }
    public Armazenamento criarArmazenamento() { return new AwsStorage("s3://" + storage.incrementAndGet()); }
    public Fila criarFila() { return new AwsQueue("sqs-" + queue.incrementAndGet()); }
    public BigDecimal custoHoraVm() { return new BigDecimal("0.10"); }
    public BigDecimal custoGb() { return new BigDecimal("0.023"); }
    public BigDecimal custoFila() { return new BigDecimal("0.40"); }
}
final class FabricaAzure implements FabricaNuvem {
    private final AtomicInteger vm = new AtomicInteger(), storage = new AtomicInteger(), queue = new AtomicInteger();
    public String provedor() { return "Azure"; }
    public MaquinaVirtual criarVm() { return new AzureVm("vm-" + vm.incrementAndGet()); }
    public Armazenamento criarArmazenamento() { return new AzureStorage("blob://" + storage.incrementAndGet()); }
    public Fila criarFila() { return new AzureQueue("sb-" + queue.incrementAndGet()); }
    public BigDecimal custoHoraVm() { return new BigDecimal("0.12"); }
    public BigDecimal custoGb() { return new BigDecimal("0.02"); }
    public BigDecimal custoFila() { return new BigDecimal("0.50"); }
}
final class Configuracao {
    private final String regiao;
    private final BigDecimal orcamento;
    private Configuracao() {
        Properties p = new Properties();
        Path arquivo = Paths.get("config.properties");
        try (InputStream in = Files.newInputStream(arquivo)) { p.load(in); }
        catch (IOException e) { throw new IllegalStateException("Nao foi possivel ler " + arquivo.toAbsolutePath(), e); }
        regiao = Objects.requireNonNull(p.getProperty("regiao"), "regiao ausente").trim();
        orcamento = new BigDecimal(Objects.requireNonNull(p.getProperty("orcamento.maximo"), "orcamento.maximo ausente").trim());
        if (regiao.isEmpty() || orcamento.signum() < 0) throw new IllegalArgumentException("Configuracao invalida");
    }
    private static final class Holder { static final Configuracao INSTANCE = new Configuracao(); }
    static Configuracao getInstance() { return Holder.INSTANCE; }
    String regiao() { return regiao; }
    BigDecimal orcamento() { return orcamento; }
}
final class RegistroAuditoria {
    final LocalDateTime dataHora;
    final String ambiente, provedor, resultado, motivo;
    RegistroAuditoria(String ambiente, String provedor, String resultado, String motivo) {
        this.dataHora = LocalDateTime.now(); this.ambiente = ambiente;
        this.provedor = provedor; this.resultado = resultado; this.motivo = motivo;
    }
    public String toString() {
        return dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss.SSS")) +
            " | " + ambiente + " | " + provedor + " | " + resultado +
            (motivo.isEmpty() ? "" : " | " + motivo);
    }
}
final class Auditoria {
    private final List<RegistroAuditoria> registros = new ArrayList<>();
    private Auditoria() {}
    private static final class Holder { static final Auditoria INSTANCE = new Auditoria(); }
    static Auditoria getInstance() { return Holder.INSTANCE; }
    synchronized void registrar(RegistroAuditoria registro) { registros.add(registro); }
    synchronized List<RegistroAuditoria> historico() { return new ArrayList<>(registros); }
}
final class Solicitacao {
    final String nome, provedor;
    final int quantidadeVm, gb;
    Solicitacao(String nome, String provedor, int quantidadeVm, int gb) {
        if (nome == null || nome.trim().isEmpty() || provedor == null || provedor.trim().isEmpty()
                || quantidadeVm < 0 || gb < 0) throw new IllegalArgumentException("Solicitacao invalida");
        this.nome = nome; this.provedor = provedor; this.quantidadeVm = quantidadeVm; this.gb = gb;
    }
}
final class Ambiente {
    final String nome, provedor, regiao;
    final List<MaquinaVirtual> maquinas;
    final Armazenamento armazenamento;
    final Fila fila;
    final BigDecimal custo;
    Ambiente(String nome, String provedor, String regiao, List<MaquinaVirtual> maquinas,
             Armazenamento armazenamento, Fila fila, BigDecimal custo) {
        this.nome = nome; this.provedor = provedor; this.regiao = regiao;
        this.maquinas = Collections.unmodifiableList(new ArrayList<>(maquinas));
        this.armazenamento = armazenamento; this.fila = fila; this.custo = custo;
        for (Recurso recurso : maquinas) validar(recurso);
        validar(armazenamento);
        validar(fila);
    }
    private void validar(Recurso recurso) {
        if (!provedor.equals(recurso.provedor())) throw new IllegalArgumentException("Recursos de provedores diferentes");
    }
    void imprimir() {
        System.out.println("ACEITO: " + nome + " | " + provedor + " | regiao " + regiao);
        for (MaquinaVirtual vm : maquinas) System.out.println("  VM: " + vm.id());
        System.out.println("  Storage: " + armazenamento.id() + " | Fila: " + fila.id());
        System.out.println("  Custo mensal: R$ " + custo.setScale(2, RoundingMode.HALF_UP));
    }
}
final class Provisionador {
    private final FabricaNuvem fabrica;
    Provisionador(FabricaNuvem fabrica) { this.fabrica = Objects.requireNonNull(fabrica); }
    Ambiente provisionar(Solicitacao s) {
        String resultado = "RECUSADO", motivo = "";
        try {
            if (!fabrica.provedor().equals(s.provedor)) throw new IllegalArgumentException("Provedor da solicitacao nao corresponde a fabrica");
            BigDecimal custo = fabrica.custoHoraVm().multiply(BigDecimal.valueOf(s.quantidadeVm))
                .multiply(BigDecimal.valueOf(730))
                .add(fabrica.custoGb().multiply(BigDecimal.valueOf(s.gb)))
                .add(fabrica.custoFila());
            if (custo.compareTo(Configuracao.getInstance().orcamento()) > 0)
                throw new IllegalArgumentException("Orcamento excedido: R$ " + custo + " > R$ " + Configuracao.getInstance().orcamento());
            // Primeiro confiro o custo. So depois crio os recursos.
            List<MaquinaVirtual> vms = new ArrayList<>();
            for (int i = 0; i < s.quantidadeVm; i++) vms.add(fabrica.criarVm());
            Ambiente ambiente = new Ambiente(s.nome, fabrica.provedor(), Configuracao.getInstance().regiao(),
                vms, fabrica.criarArmazenamento(), fabrica.criarFila(), custo);
            resultado = "ACEITO";
            return ambiente;
        } catch (IllegalArgumentException e) {
            motivo = e.getMessage();
            System.out.println("RECUSADO: " + s.nome + " | " + motivo);
            return null;
        } finally {
            Auditoria.getInstance().registrar(new RegistroAuditoria(s.nome, s.provedor, resultado, motivo));
        }
    }
}
public class Main {
    public static void main(String[] args) throws Exception {
        // Aqui escolho qual fabrica usar para cada nuvem.
        FabricaNuvem aws = new FabricaAws();
        FabricaNuvem azure = new FabricaAzure();
        Configuracao.getInstance();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Callable<Ambiente>> tarefas = Arrays.asList(
            () -> new Provisionador(aws).provisionar(new Solicitacao("portal", "Aws", 2, 100)),
            () -> new Provisionador(aws).provisionar(new Solicitacao("api", "Aws", 1, 50)),
            () -> new Provisionador(azure).provisionar(new Solicitacao("analytics", "Azure", 1, 80)),
            () -> new Provisionador(azure).provisionar(new Solicitacao("gigante", "Azure", 1000, 99999))
        );
        try {
            List<Future<Ambiente>> futuros = pool.invokeAll(tarefas);
            for (Future<Ambiente> futuro : futuros) {
                Ambiente a = futuro.get(); if (a != null) a.imprimir();
            }
        } finally { pool.shutdown(); }
        System.out.println("\n=== HISTORICO DE AUDITORIA (ordem de registro) ===");
        List<RegistroAuditoria> historico = Auditoria.getInstance().historico();
        for (RegistroAuditoria r : historico) System.out.println(r);
        if (historico.size() != 4) {
            throw new AssertionError("O historico precisa ter 4 registros");
        }
    }
}
