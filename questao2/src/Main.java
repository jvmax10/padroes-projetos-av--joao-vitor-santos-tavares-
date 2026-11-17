import java.math.*;
import java.time.*;
import java.util.*;

final class Dinheiro {
    static BigDecimal de(String n) { return new BigDecimal(n); }
    static BigDecimal pct(BigDecimal v, String taxa) { return v.multiply(de(taxa)); }
    static String br(BigDecimal v) { return "R$ " + v.setScale(2, RoundingMode.HALF_UP); }
}
final class Lance {
    final String participante;
    final BigDecimal valor;
    final LocalDateTime dataHora = LocalDateTime.now();
    Lance(String participante, BigDecimal valor) { this.participante = participante; this.valor = valor; }
}
interface ObservadorLance { void atualizar(Lote lote, Lance lance); }
final class Participante implements ObservadorLance {
    final String nome;
    Participante(String nome) { this.nome = nome; }
    public void atualizar(Lote lote, Lance lance) {
        if (!nome.equals(lance.participante))
            System.out.println("[" + nome + "] " + lote.nome() + ": novo lance " + Dinheiro.br(lance.valor));
    }
}
final class PainelLeiloeiro implements ObservadorLance {
    public void atualizar(Lote lote, Lance lance) {
        System.out.println("[Painel] " + lote.nome() + " | maior " + Dinheiro.br(lote.lanceAtual())
            + " | lances recebidos " + lote.totalLances());
    }
}
final class AuditorLances implements ObservadorLance {
    private final List<String> registros = new ArrayList<>();
    public void atualizar(Lote lote, Lance lance) {
        String registro = lance.dataHora + " | " + lote.nome() + " | " + lance.participante + " | " + Dinheiro.br(lance.valor);
        registros.add(registro);
        System.out.println("[Auditor] " + registro);
    }
    List<String> historico() { return Collections.unmodifiableList(registros); }
}
interface EstrategiaComissao { BigDecimal calcular(BigDecimal venda); String descricao(); }
final class ComissaoArte implements EstrategiaComissao {
    public BigDecimal calcular(BigDecimal venda) { return Dinheiro.pct(venda, "0.20"); }
    public String descricao() { return "Arte 20%"; }
}
final class ComissaoVeiculos implements EstrategiaComissao {
    public BigDecimal calcular(BigDecimal venda) { return Dinheiro.pct(venda, "0.08").min(Dinheiro.de("5000")); }
    public String descricao() { return "Veiculos 8%, teto R$ 5000"; }
}
final class ComissaoImoveis implements EstrategiaComissao {
    public BigDecimal calcular(BigDecimal venda) { return Dinheiro.pct(venda, "0.03").add(Dinheiro.de("1500")); }
    public String descricao() { return "Imoveis 3% + R$ 1500"; }
}
final class ComissaoEletronicos implements EstrategiaComissao {
    public BigDecimal calcular(BigDecimal venda) { return Dinheiro.pct(venda, "0.12").max(Dinheiro.de("50")); }
    public String descricao() { return "Eletronicos 12%, minimo R$ 50"; }
}
final class Lote {
    private final String nome, categoria;
    private final BigDecimal precoInicial, reserva;
    private BigDecimal atual;
    private final List<Lance> lances = new ArrayList<>();
    private final List<ObservadorLance> observadores = new ArrayList<>();
    private EstrategiaComissao comissao;
    private boolean aberto = true, vendido;
    Lote(String nome, String categoria, BigDecimal inicial, BigDecimal reserva, EstrategiaComissao comissao) {
        if (inicial.signum() < 0 || reserva.signum() < 0) throw new IllegalArgumentException("Precos negativos");
        this.nome = nome; this.categoria = categoria; this.precoInicial = inicial;
        this.reserva = reserva; this.atual = inicial; this.comissao = Objects.requireNonNull(comissao);
    }
    String nome() { return nome; }
    BigDecimal lanceAtual() { return atual; }
    int totalLances() { return lances.size(); }
    void acompanhar(ObservadorLance obs) { if (!observadores.contains(obs)) observadores.add(obs); }
    void deixarDeAcompanhar(ObservadorLance obs) { observadores.remove(obs); }
    void trocarComissao(EstrategiaComissao nova) {
        if (!aberto) throw new IllegalStateException("Lote ja encerrado");
        comissao = Objects.requireNonNull(nova);
        System.out.println("Campanha " + nome + ": " + comissao.descricao());
    }
    boolean ofertar(Participante participante, BigDecimal valor) {
        if (!aberto) { System.out.println("Lance recusado: lote encerrado"); return false; }
        BigDecimal minimo = atual.multiply(Dinheiro.de("1.05"));
        if (valor.compareTo(minimo) < 0) {
            System.out.println("Lance recusado de " + participante.nome + " em " + nome + ": minimo " + Dinheiro.br(minimo));
            return false;
        }
        atual = valor;
        Lance lance = new Lance(participante.nome, valor);
        lances.add(lance);
        for (ObservadorLance obs : new ArrayList<>(observadores)) obs.atualizar(this, lance);
        return true;
    }
    Venda encerrar() {
        if (!aberto) throw new IllegalStateException("Lote ja encerrado");
        aberto = false;
        vendido = !lances.isEmpty() && atual.compareTo(reserva) >= 0;
        System.out.println("Encerramento " + nome + ": " + (vendido ? "VENDIDO" : "NAO VENDIDO"));
        if (!vendido) return null;
        return new Venda(nome, atual, comissao.calcular(atual));
    }
}
final class Venda {
    final String lote;
    final BigDecimal valor, comissao;
    Venda(String lote, BigDecimal valor, BigDecimal comissao) {
        this.lote = lote; this.valor = valor; this.comissao = comissao;
    }
    void imprimirAcerto() {
        System.out.println("ACERTO " + lote + " | venda " + Dinheiro.br(valor) + " | comissao "
            + Dinheiro.br(comissao) + " | liquido " + Dinheiro.br(valor.subtract(comissao)));
    }
}
interface Fatura {
    BigDecimal total();
    void imprimirItens();
}
final class FaturaBase implements Fatura {
    private final Venda venda;
    FaturaBase(Venda venda) { this.venda = Objects.requireNonNull(venda); }
    public BigDecimal total() { return venda.valor; }
    public void imprimirItens() { System.out.println("Lote " + venda.lote + " | valor de venda " + Dinheiro.br(venda.valor)); }
}
abstract class ServicoDecorator implements Fatura {
    protected final Fatura base;
    ServicoDecorator(Fatura base) { this.base = Objects.requireNonNull(base); }
    protected abstract String descricao();
    protected abstract BigDecimal custo();
    public BigDecimal total() { return base.total().add(custo()); }
    public void imprimirItens() {
        base.imprimirItens();
        System.out.println("  + " + descricao() + ": " + Dinheiro.br(custo()));
    }
}
final class SeguroTransporte extends ServicoDecorator {
    private final BigDecimal valorVenda;
    SeguroTransporte(Fatura base, BigDecimal valorVenda) { super(base); this.valorVenda = valorVenda; }
    protected String descricao() { return "Seguro transporte (1,5%)"; }
    protected BigDecimal custo() { return Dinheiro.pct(valorVenda, "0.015"); }
}
final class CertificadoAutenticidade extends ServicoDecorator {
    CertificadoAutenticidade(Fatura base) { super(base); }
    protected String descricao() { return "Certificado de autenticidade"; }
    protected BigDecimal custo() { return Dinheiro.de("300"); }
}
final class EmbalagemEspecial extends ServicoDecorator {
    EmbalagemEspecial(Fatura base) { super(base); }
    protected String descricao() { return "Embalagem especial"; }
    protected BigDecimal custo() { return Dinheiro.de("180"); }
}
final class EntregaExpressa extends ServicoDecorator {
    EntregaExpressa(Fatura base) { super(base); }
    protected String descricao() { return "Entrega expressa"; }
    protected BigDecimal custo() { return Dinheiro.de("450"); }
}
public class Main {
    public static void main(String[] args) {
        Participante ana = new Participante("Ana");
        Participante bruno = new Participante("Bruno");
        Participante carla = new Participante("Carla");
        PainelLeiloeiro painel = new PainelLeiloeiro();
        AuditorLances auditor = new AuditorLances();
        Lote quadro = new Lote("Quadro modernista", "Arte", Dinheiro.de("1000"), Dinheiro.de("1400"), new ComissaoArte());
        Lote carro = new Lote("Carro classico", "Veiculos", Dinheiro.de("20000"), Dinheiro.de("30000"), new ComissaoVeiculos());
        for (Lote lote : Arrays.asList(quadro, carro)) {
            lote.acompanhar(ana);
            lote.acompanhar(bruno);
            lote.acompanhar(carla);
            lote.acompanhar(painel);
            lote.acompanhar(auditor);
        }
        quadro.ofertar(ana, Dinheiro.de("1020")); // abaixo do minimo
        quadro.ofertar(ana, Dinheiro.de("1100"));
        quadro.ofertar(bruno, Dinheiro.de("1200"));
        quadro.deixarDeAcompanhar(carla);
        quadro.trocarComissao(new ComissaoEletronicos());
        quadro.ofertar(carla, Dinheiro.de("1500"));
        carro.ofertar(bruno, Dinheiro.de("21000"));
        carro.ofertar(ana, Dinheiro.de("22000"));
        Venda venda = quadro.encerrar();
        Venda naoVendida = carro.encerrar();
        quadro.ofertar(bruno, Dinheiro.de("2000")); // lote fechado
        if (venda == null || naoVendida != null) {
            throw new AssertionError("Erro ao fechar os lotes");
        }
        venda.imprimirAcerto();
        Fatura fatura = new FaturaBase(venda);
        fatura = new SeguroTransporte(fatura, venda.valor);
        fatura = new CertificadoAutenticidade(fatura);
        fatura = new EmbalagemEspecial(fatura);
        fatura = new EmbalagemEspecial(fatura); // pedido duas vezes
        fatura = new EntregaExpressa(fatura);
        System.out.println("\n=== FATURA DO COMPRADOR ===");
        fatura.imprimirItens();
        System.out.println("TOTAL A PAGAR: " + Dinheiro.br(fatura.total()));
        System.out.println("Registros de auditoria: " + auditor.historico().size());
    }
}
