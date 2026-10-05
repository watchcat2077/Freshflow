"""layered-architecture.uxf and layered-architecture-all-services.uxf (spec-01 s2.1, s2.2)."""
import os
from uxf import Diagram, render, wrap

OUT = os.environ.get("UML_OUT", os.path.dirname(os.path.abspath(__file__)))
LAYER_BG = {"presentation": "#DDEBF7", "application": "#E2F0D9", "domain": "#FFF2CC", "infrastructure": "#E7E6E6"}


def layer(d, x, y, w, name, classes, bg, h=None, valign=None):
    if h is None:
        h = 40 + 15 * len(classes)
        h = (h + 9) // 10 * 10
    extra = "halign=left" + (f"\nvalign={valign}" if valign else "")
    return d.package(x, y, w, h, name, lines=classes, bg=bg, extra=extra)


def build_inventory():
    d = Diagram()
    d.text(0, 0, 1000, 30, "Layered architecture of inventory-service (com.freshflow.inventory): spec-01 s2.1 and s2.2", wrap=False)
    d.package(0, 40, 1020, 700, "inventory-service", bg="#FAFAFA", extra="layer=-1")
    pres = layer(d, 40, 100, 400, "presentation",
                 ["BatchController", "CreateBatchRequestDto", "BatchResponseDto", "MoneyDto", "ErrorResponseDto"],
                 LAYER_BG["presentation"])
    app = layer(d, 40, 270, 400, "application",
                ["BatchApplicationService", "ApplyItemSoldHandler", "RegisterGoodsReceiptCommand",
                 "ApplyItemSoldCommand", "BatchView", "ApplicationResult"], LAYER_BG["application"])
    dom = layer(d, 40, 450, 400, "domain",
                ["StockBatch  <<Aggregate Root>>", "FefoDeductionPolicy  <<Domain Service>>", "DeductionPlan",
                 "Money, Sku, Quantity, ExpiryDate, BatchId", "BatchStatus  <<enum>>", "DomainRuleViolation",
                 "BatchReceived, StockLevelChanged  <<Domain Event>>", "DomainEventPublisher  <<Port>>"],
                LAYER_BG["domain"], h=180)
    inf = layer(d, 560, 100, 420, "infrastructure",
                ["JpaStockBatchRepository  (implements the port)", "StockBatchJpaEntity, StockBatchJpaRepository",
                 "ConsumedEventJpaEntity, ConsumedEventJpaRepository", "StockBatchMapper",
                 "KafkaDomainEventPublisher  (implements the port)", "ItemSoldMessageHandler",
                 "InventoryMessagingConfiguration", "ClockConfiguration", "DomainServiceConfiguration"],
                LAYER_BG["infrastructure"], h=540, valign="top")
    rep = d.package(580, 530, 380, 80, "infrastructure.repository",
                    lines=["StockBatchRepository  <<Port>>  (interface; ADR 0005)"], bg="#FFFFFF",
                    extra="halign=left\nlayer=1")
    # dependencies (dashed): outer layers depend on inner ones
    d.relation([pres.bottom, app.top], lt=".>")
    d.relation([app.bottom, dom.top], lt=".>")
    d.relation([app.right_at(340), (520, 340), (520, 550), rep.left_at(550)], lt=".>", label="ADR 0005")
    d.relation([rep.left_at(590), dom.right_at(590)], lt=".>", label="save / find")
    d.relation([inf.bottom_at(640), (640, 680), (240, 680), dom.bottom], lt=".>",
               label="implements DomainEventPublisher; maps JPA entity <-> StockBatch")
    d.note(0, 760, 1020, 90, wrap(
        "Course specification layer -> FreshFlow package: Presentation = presentation; Service = application; "
        "Domain = domain; Data Access (infrastructure) = infrastructure, which also holds the Kafka bindings, "
        "mappers and configuration. Dependency rule (spec-01 s2.2): presentation -> application -> domain <- "
        "infrastructure; the domain package imports nothing (no Spring, JPA, Jackson or Kafka). Repository "
        "interfaces are declared in infrastructure.repository with their JPA implementations beside them "
        "(subject coordinator's ruling, ADR 0005).", 1020), bg="#FFFBE6")
    path = os.path.join(OUT, "layered-architecture.uxf")
    d.save(path)
    return render(path)


def build_all():
    d = Diagram()
    d.text(0, 0, 1400, 30, "The four layers in every service, with the domain-layer pattern chosen per bounded context (spec-01 s2.3)", wrap=False)
    cols = [
        ("inventory-service", "Domain Model",
         ["BatchController"],
         ["BatchApplicationService", "ApplyItemSoldHandler", "commands, BatchView"],
         ["StockBatch <<AR>>", "FefoDeductionPolicy", "value objects, domain events", "DomainEventPublisher port"],
         ["JpaStockBatchRepository", "KafkaDomainEventPublisher", "ItemSoldMessageHandler", ".repository: StockBatchRepository"],
         "Domain Model (spec-01 s2.3)"),
        ("sales-service", "Transaction Script",
         ["TransactionController"],
         ["TransactionApplicationService", "  (the transaction script)", "PriceSnapshotFeed port",
          "SaleTransactionRecorder port", "SaleEventPublisher port"],
         ["SaleTransaction, LineItem,", "PriceSnapshot: plain data,", "no aggregate, no repository"],
         ["JpaSaleTransactionRecorder", "KafkaSaleEventPublisher", "PriceRulePublishedSnapshotFeed"],
         "Transaction Script (ADR 0002)"),
        ("pricing-service", "Domain Model",
         ["PriceController, MarkdownPlanController"],
         ["PriceApplicationService", "MarkdownPlanApplicationService", "commands, views"],
         ["PriceRule, MarkdownPlan <<AR>>", "MarginFloorPolicy, MarginFloorAssessment", "PriceRuleEventPublisher,", "MarkdownEventPublisher ports"],
         ["JpaPriceRuleRepository, JpaMarkdownPlanRepository", "KafkaPriceRuleEventPublisher", "MarkdownRecommendedMessageHandler", ".repository: PriceRuleRepository,", "  MarkdownPlanRepository"],
         "Domain Model (spec-01 s2.3)"),
        ("insight-service", "CQRS read side",
         ["VelocityController, RiskController,", "AgentTraceController, BriefingController"],
         ["MarkdownAgentOrchestrator, BriefingOrchestrator", "VelocityQueryService, RiskQueryService", "read models, agent ports"],
         ["RiskAssessmentPolicy  <<Domain Service>>", "BatchRiskView  <<Value Object>>  (ADR 0006)"],
         ["InsightTopologyConfiguration (Streams)", "LangChain4jMarkdownDecisionAgent,", "LangChain4jBriefingAgent", "RestBatchDetailsProvider",
          "JpaAgentTraceStore, JpaBriefingStore"],
         "CQRS read side + domain package (ADR 0006)"),
    ]
    W, GAP = 320, 40
    for i, (svc, _pat, pres, app, dom, inf, pattern) in enumerate(cols):
        x = i * (W + GAP)
        d.package(x, 40, W, 700, svc, bg="#FAFAFA", extra="layer=-1")
        p = layer(d, x + 20, 90, W - 40, "presentation", pres, LAYER_BG["presentation"], h=80)
        a = layer(d, x + 20, 200, W - 40, "application", app, LAYER_BG["application"], h=120)
        m = layer(d, x + 20, 350, W - 40, "domain", dom, LAYER_BG["domain"], h=100)
        f = layer(d, x + 20, 480, W - 40, "infrastructure", inf, LAYER_BG["infrastructure"], h=130)
        d.text(x + 20, 630, W - 40, 60, "*Pattern: " + pattern + "*", wrap=True, extra="halign=center")
        d.relation([p.bottom, a.top], lt=".>")
        d.relation([a.bottom, m.top], lt=".>")
        d.relation([f.top, m.bottom], lt=".>")
    d.note(0, 760, 1400, 60, wrap(
        "Dependency rule in every service: presentation -> application -> domain <- infrastructure (spec-01 s2.2). "
        "Ports follow the pattern: declared in domain for Domain Model services, in application for sales-service and "
        "insight-service (ADR 0002); repository interfaces live in infrastructure.repository (ADR 0005). Invariants live in "
        "the innermost layer the pattern provides: the aggregate for Domain Model, the transaction script for sales-service, "
        "and the RiskAssessmentPolicy domain service for insight-service (spec-01 s2.5, ADR 0006).", 1400), bg="#FFFBE6")
    path = os.path.join(OUT, "layered-architecture-all-services.uxf")
    d.save(path)
    return render(path)


if __name__ == "__main__":
    print(build_inventory())
    print(build_all())
